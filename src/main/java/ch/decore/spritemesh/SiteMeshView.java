package ch.decore.spritemesh;

import org.sitemesh.DecoratorSelector;
import org.sitemesh.SiteMeshContext;
import org.sitemesh.config.PathBasedDecoratorSelector;
import org.sitemesh.config.PathMapper;
import org.sitemesh.content.Content;
import org.sitemesh.content.ContentProcessor;
import org.sitemesh.webapp.WebAppContext;
import org.sitemesh.webapp.contentfilter.HttpServletRequestFilterable;
import org.sitemesh.webapp.contentfilter.HttpServletResponseBuffer;
import org.sitemesh.webapp.contentfilter.ResponseMetaData;
import org.sitemesh.webapp.contentfilter.Selector;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.util.Locale;
import java.util.Map;

public class SiteMeshView implements View {
    private ContentProcessor contentProcessor;
    private DecoratorSelector<SiteMeshContext> decoratorSelector;
    private Selector selector;
    private ViewResolver viewResolver;
    private View view;
    private Locale locale;

    public SiteMeshView(ViewResolver viewResolver, String viewName, Locale locale, ContentProcessor contentProcessor,
                        DecoratorSelector<SiteMeshContext> decoratorSelector, Selector selector)
            throws Exception {
        this.contentProcessor = contentProcessor;
        this.decoratorSelector = decoratorSelector;
        this.selector = selector;
        this.viewResolver = viewResolver;
        this.locale = locale;
        this.view = viewResolver.resolveViewName(viewName, locale);
    }

    @Override
    public String getContentType() {
        return view.getContentType();
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String exclusionPattern = selector.excludePatternInUse(request);
        if (exclusionPattern != null) {
            // Ability to override exclusion by more specific pattern
            if (decoratorSelector instanceof PathBasedDecoratorSelector) {
                PathBasedDecoratorSelector<SiteMeshContext> pbds = (PathBasedDecoratorSelector<SiteMeshContext>) decoratorSelector;
                String decoratorPattern = pbds.getPathMapper().getPatternInUse(WebAppContext.getRequestPath(request));
                if (decoratorPattern == null) {
                    // there is no decorator rule for this exclusion pattern
                    // -> Normal view rendering ..
                    view.render(model, request, response);
                    return;
                }
                if (PathMapper.isMoreSpecific(exclusionPattern, decoratorPattern)) {
                    // if the exclusion type is more specific
                    // -> Normal view rendering ..
                    view.render(model, request, response);
                    return;
                }
            }
        }
        // OK, let's decorate .. :)
        bufferAndPostProcess(model, request, response);
    }

    protected void bufferAndPostProcess(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
            throws Exception {

        // Apply next filter/servlet, writing response to buffer.
        final ResponseMetaData metaData = new ResponseMetaData();
        InternalHttpServletResponseBuffer responseBuffer = createResponseBuffer(request, response, metaData, selector);

        // -> Render view here ...
        view.render(model, wrapRequest(request), responseBuffer);
        CharBuffer buffer = responseBuffer.getBuffer();

        // If content was buffered, post-process it.
        boolean processed = false;
        if (buffer != null && !responseBuffer.bufferingWasDisabled()) {
            processed = postProcess(buffer, request, response, metaData, model);
        }

        if (!response.isCommitted()) {
            responseBuffer.preCommit();
        }

        // If no decorator applied, and we have some buffered content, write the original.
        if (buffer != null && !processed) {
            writeOriginal(response, buffer, responseBuffer);
        }
    }

    protected InternalHttpServletResponseBuffer createResponseBuffer(HttpServletRequest request, HttpServletResponse response,
                                                                     ResponseMetaData metaData, Selector selector) {
        return new InternalHttpServletResponseBuffer(response, metaData, request, selector);
    }

    protected HttpServletRequest wrapRequest(HttpServletRequest request) {
        return new HttpServletRequestFilterable(request);
    }

    protected boolean postProcess(CharBuffer buffer, HttpServletRequest request, HttpServletResponse response,
                                  ResponseMetaData metaData, Map<String, ?> model)
            throws IOException, ServletException {
        SiteMeshContext context = createContext(request, response, metaData, model);
        Content content = contentProcessor.build(buffer, context);
        if (content == null) {
            return false;
        }

        String[] decoratorPaths = decoratorSelector.selectDecoratorPaths(content, context);
        for (String decoratorPath : decoratorPaths) {
            content = context.decorate(decoratorPath, content);
        }

        if (content == null) {
            return false;
        }
        try {
            content.getData().writeValueTo(response.getWriter());
        } catch (IllegalStateException ise) {  // If getOutputStream() has already been called
            content.getData().writeValueTo(new PrintStream(response.getOutputStream()));
        }
        return true;
    }

    protected void writeOriginal(HttpServletResponse response, CharBuffer buffer, HttpServletResponseBuffer responseBuffer)
            throws IOException {
        if (responseBuffer.isBufferStreamBased()) {
            PrintWriter writer = new PrintWriter(response.getOutputStream());
            writer.append(buffer);
            writer.flush(); // Flush writer to underlying outputStream.
            response.getOutputStream().flush();
        } else {
            PrintWriter writer = response.getWriter();
            writer.append(buffer);
            response.getWriter().flush();
        }
    }

    /**
     * Create a context for the current request. This method can be overridden to allow for other
     * types of context.
     */
    protected SiteMeshContext createContext(HttpServletRequest request, HttpServletResponse response,
                                            ResponseMetaData metaData, Map<String, ?> model) {
        return new SiteMeshViewResolverContext(viewResolver, model, locale, contentProcessor, request, response, metaData);
    }

    protected static class InternalHttpServletResponseBuffer extends HttpServletResponseBuffer {
        private final HttpServletResponse response;
        private final ResponseMetaData metaData;
        private final HttpServletRequest request;

        public InternalHttpServletResponseBuffer(HttpServletResponse response, ResponseMetaData metaData,
                                                 HttpServletRequest request, Selector selector) {
            super(response, metaData, selector);
            this.response = response;
            this.metaData = metaData;
            this.request = request;
        }

        @Override
        public void preCommit() {
            // Ensure both content and decorators are used to generate HTTP caching headers.
            long lastModified = metaData.getLastModified();
            long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            if (lastModified > -1 && !response.containsHeader("Last-Modified")) {
                if (ifModifiedSince < (lastModified / 1000 * 1000)) {
                    response.setDateHeader("Last-Modified", lastModified);
                } else {
                    response.reset();
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                }
            }
        }
    }
}

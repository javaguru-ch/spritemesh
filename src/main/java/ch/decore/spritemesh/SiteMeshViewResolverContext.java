package ch.decore.spritemesh;

import org.sitemesh.BaseSiteMeshContext;
import org.sitemesh.config.PathMapper;
import org.sitemesh.content.Content;
import org.sitemesh.content.ContentProcessor;
import org.sitemesh.webapp.WebAppContext;
import org.sitemesh.webapp.contentfilter.BasicSelector;
import org.sitemesh.webapp.contentfilter.HttpServletRequestFilterable;
import org.sitemesh.webapp.contentfilter.HttpServletResponseBuffer;
import org.sitemesh.webapp.contentfilter.ResponseMetaData;
import org.springframework.web.servlet.ViewResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.Map;

public class SiteMeshViewResolverContext extends BaseSiteMeshContext {
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ResponseMetaData metaData;
    private ViewResolver viewResolver;
    private Locale locale;
    private Map<String, ?> model;

    public SiteMeshViewResolverContext(ViewResolver viewResolver, Map<String, ?> model, Locale locale,
                                       ContentProcessor contentProcessor,
                                       HttpServletRequest request, HttpServletResponse response,
                                       ResponseMetaData metaData) {
        super(contentProcessor);
        this.request = request;
        this.response = response;
        this.metaData = metaData;
        this.viewResolver = viewResolver;
        this.locale = locale;
        this.model = model;
    }

    @Override
    public String getPath() {
        return WebAppContext.getRequestPath(request);
    }

    @Override
    protected void decorate(String decoratorPath, Content content, Writer out) throws IOException {
        HttpServletRequest filterableRequest = new HttpServletRequestFilterable(request);
        // Wrap response so output gets buffered.
        HttpServletResponseBuffer responseBuffer = new HttpServletResponseBuffer(response, metaData, new BasicSelector(new PathMapper<Boolean>(), false) {
            @Override
            public boolean shouldBufferForContentType(String contentType, String mimeType, String encoding) {
                return true; // We know we should buffer.
            }
        });
        responseBuffer.setContentType(response.getContentType()); // Trigger buffering.

        // It's possible that this is reentrant, so we need to take a copy
        // of additional request attributes so we can restore them afterwards.
        Object oldContent = request.getAttribute(WebAppContext.CONTENT_KEY);
        Object oldContext = request.getAttribute(WebAppContext.CONTEXT_KEY);

        request.setAttribute(WebAppContext.CONTENT_KEY, content);
        request.setAttribute(WebAppContext.CONTEXT_KEY, this);

        try {
            // Main dispatch.
            viewResolver.resolveViewName(decoratorPath, locale).render(model, filterableRequest, responseBuffer);

            // Write out the buffered output.
            out.append(responseBuffer.getBuffer());
        } catch (Exception e) {
            //noinspection ThrowableInstanceNeverThrown
            throw (IOException) new IOException("Could not dispatch to decorator").initCause(e);
        } finally {
            // Restore previous state.
            request.setAttribute(WebAppContext.CONTENT_KEY, oldContent);
            request.setAttribute(WebAppContext.CONTEXT_KEY, oldContext);
        }
    }
}

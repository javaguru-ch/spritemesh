package ch.decore.spritemesh;

import org.sitemesh.DecoratorSelector;
import org.sitemesh.SiteMeshContext;
import org.sitemesh.content.ContentProcessor;
import org.sitemesh.webapp.contentfilter.*;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import java.util.Locale;

public class SiteMeshViewResolver implements ViewResolver {
    private ViewResolver delegate;
    private ContentProcessor contentProcessor;
    private DecoratorSelector<SiteMeshContext> decoratorSelector;
    private Selector selector;

    public SiteMeshViewResolver(ViewResolver delegate, ContentProcessor contentProcessor,
                                DecoratorSelector<SiteMeshContext> decoratorSelector, Selector selector) {
        this.delegate = delegate;
        this.contentProcessor = contentProcessor;
        this.decoratorSelector = decoratorSelector;
        this.selector = selector;
    }

    @Override
    public View resolveViewName(String viewName, Locale locale) throws Exception {
        return new SiteMeshView(delegate, viewName, locale, contentProcessor, decoratorSelector, selector);
    }
}
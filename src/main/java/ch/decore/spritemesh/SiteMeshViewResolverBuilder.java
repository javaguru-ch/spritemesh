package ch.decore.spritemesh;

import org.sitemesh.SiteMeshContext;
import org.sitemesh.builder.BaseSiteMeshBuilder;
import org.sitemesh.config.PathMapper;
import org.sitemesh.webapp.contentfilter.BasicSelector;
import org.sitemesh.webapp.contentfilter.Selector;
import org.springframework.web.servlet.ViewResolver;

import java.util.Arrays;
import java.util.List;

public class SiteMeshViewResolverBuilder extends BaseSiteMeshBuilder<SiteMeshViewResolverBuilder, SiteMeshContext, SiteMeshViewResolver> {
    private ViewResolver delegate;
    private Selector customSelector;
    private boolean includeErrorPages;
    private List<String> mimeTypes;
    private PathMapper<Boolean> excludesMapper = new PathMapper<Boolean>();

    public SiteMeshViewResolverBuilder(ViewResolver delegate) {
        this.delegate = delegate;
    }

    /**
     * See {@link BaseSiteMeshBuilder#setupDefaults()}.
     * In addition to the parent setup, this also calls {@link #setMimeTypes(String[])} with
     * <code>{"text/html"}</code>.
     */
    @Override
    protected void setupDefaults() {
        super.setupDefaults();
        setMimeTypes("text/html");
        setIncludeErrorPages(false);
    }

    // --------------------------------------------------------------
    // Selector setup.

    /**
     * Add a path to be excluded by SiteMesh.
     */
    public SiteMeshViewResolverBuilder addExcludedPath(String exclude) {
        excludesMapper.put(exclude, true);
        return self();
    }

    // --------------------------------------------------------------
    // Selector setup.

    /**
     * Set MIME types that the Filter should intercept. The default
     * is <code>{"text/html"}</code>.
     *
     * <p>Note: The MIME types are ignored if {@link #setCustomSelector(Selector)} is called.</p>
     */
    public SiteMeshViewResolverBuilder setMimeTypes(String... mimeTypes) {
        this.mimeTypes = Arrays.asList(mimeTypes);
        return self();
    }

    /**
     * Set MIME types that the Filter should intercept. The default
     * is <code>{"text/html"}</code>.
     *
     * <p>Note: The MIME types are ignored if {@link #setCustomSelector(Selector)} is called.</p>
     */
    public SiteMeshViewResolverBuilder setMimeTypes(List<String> mimeTypes) {
        this.mimeTypes = mimeTypes;
        return self();
    }

    /**
     * Set if the error pages should be decorated as well.
     * The default is <code>false</code>.
     *
     * <p>Note: The error pages inclusion is ignored if {@link #setCustomSelector(Selector)} is called.</p>
     */
    public SiteMeshViewResolverBuilder setIncludeErrorPages(boolean includeErrorPages) {
        this.includeErrorPages = includeErrorPages;
        return self();
    }
    /**
     * Set a custom {@link Selector}.
     *
     * <p>Note: If this is called, it will override any MIME types
     * passed to {@link #setMimeTypes(String[])} as these are specific
     * to the default Selector.</p>
     */
    public SiteMeshViewResolverBuilder setCustomSelector(Selector selector) {
        this.customSelector = selector;
        return self();
    }

    /**
     * Get configured {@link Selector}.
     */
    public Selector getSelector() {
        if (customSelector != null) {
            return customSelector;
        } else {
            return new BasicSelector(excludesMapper, includeErrorPages, mimeTypes.toArray(new String[mimeTypes.size()]));
        }
    }

    @Override
    public SiteMeshViewResolver create() throws IllegalStateException {
        return new SiteMeshViewResolver(delegate, getContentProcessor(), getDecoratorSelector(), getSelector());
    }
}

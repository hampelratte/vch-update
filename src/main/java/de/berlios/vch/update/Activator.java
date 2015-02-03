package de.berlios.vch.update;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.servlet.ServletException;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.log.LogService;

import de.berlios.vch.i18n.ResourceBundleLoader;
import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.web.ResourceHttpContext;

@Component
@Provides
public class Activator implements ResourceBundleProvider {

    @Requires
    private LogService logger;
    
    @Requires
    private HttpService httpService;
    
    private BundleContext ctx;
    
    private ResourceBundle resourceBundle;
    
    public Activator(BundleContext ctx) {
        this.ctx = ctx;
    }
    
    @Validate
    public void start() {
        try {
            registerStaticContext();
        } catch (Exception e) {
            logger.log(LogService.LOG_ERROR, "Couldn't register config servlet", e);
        }
    }

    private void registerStaticContext() throws ServletException, NamespaceException {
        // register resource context for static files
        ResourceHttpContext resourceHttpContext = new ResourceHttpContext(ctx, logger);
        httpService.registerResources(UpdateServlet.STATIC_PATH, "/htdocs", resourceHttpContext);
    }

    @Invalidate
    public void stop() {
        if(httpService != null) {
            httpService.unregister(UpdateServlet.STATIC_PATH);
        }
    }

    @Override
    public ResourceBundle getResourceBundle() {
        if(resourceBundle == null) {
            try {
                logger.log(LogService.LOG_INFO, "Loading resource bundle for " + getClass().getSimpleName());
                resourceBundle = ResourceBundleLoader.load(ctx, Locale.getDefault());
            } catch (IOException e) {
                logger.log(LogService.LOG_ERROR, "Couldn't load resource bundle", e);
            }
        }
        return resourceBundle;
    }
}

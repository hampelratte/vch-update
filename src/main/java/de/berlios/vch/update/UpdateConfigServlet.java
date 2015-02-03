package de.berlios.vch.update;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.log.LogService;

import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.web.NotifyMessage;
import de.berlios.vch.web.NotifyMessage.TYPE;
import de.berlios.vch.web.TemplateLoader;
import de.berlios.vch.web.menu.IWebMenuEntry;
import de.berlios.vch.web.menu.WebMenuEntry;
import de.berlios.vch.web.servlets.VchHttpServlet;

@Component
public class UpdateConfigServlet extends VchHttpServlet {

    public static String PATH = "/config/extensions";

    @Requires
    private ObrManager obrManager;

    @Requires(filter = "(instance.name=vch.web.update)")
    private ResourceBundleProvider rbp;

    @Requires
    private LogService logger;

    @Requires
    private TemplateLoader templateLoader;

    @Requires
    private HttpService httpService;

    private BundleContext ctx;

    private ServiceRegistration menuReg;

    public UpdateConfigServlet(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Map<String, Object> params = new HashMap<String, Object>();

            if (req.getParameter("add_obr") != null) {
                String obrUri = req.getParameter("obr");
                try {
                    obrManager.addOBR(obrUri);
                    addNotify(req, new NotifyMessage(TYPE.INFO, rbp.getResourceBundle().getString("info.obr_added")));
                } catch (Exception e) {
                    String msg = rbp.getResourceBundle().getString("error.add_obr");
                    logger.log(LogService.LOG_ERROR, msg, e);
                    addNotify(req, new NotifyMessage(TYPE.ERROR, msg, e));
                }
            } else if (req.getParameter("remove_obrs") != null) {
                String[] obrs = req.getParameterValues("obrs");
                if (obrs != null) {
                    for (String id : obrs) {
                        obrManager.removeOBR(id);
                    }
                }
            }

            params.put("TITLE", rbp.getResourceBundle().getString("I18N_CONFIG_TITLE"));
            params.put("SERVLET_URI",
                    req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort() + req.getServletPath());
            params.put("OBRS", obrManager.getOBRs());
            params.put("ACTION", PATH);
            params.put("NOTIFY_MESSAGES", getNotifyMessages(req));

            String page = templateLoader.loadTemplate("extensions_config.ftl", params);
            resp.getWriter().print(page);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        get(req, resp);
    }

    @Validate
    public void start() {
        try {
            registerServlet();
        } catch (Exception e) {
            logger.log(LogService.LOG_ERROR, "Couldn't register config servlet", e);
        }
    }

    private void registerServlet() throws ServletException, NamespaceException {
        // register the configuration servlet
        httpService.registerServlet(PATH, this, null, null);

        // register web interface menu
        WebMenuEntry menu = new WebMenuEntry();
        menu.setTitle(rbp.getResourceBundle().getString("I18N_EXTENSIONS"));
        menu.setPreferredPosition(UpdateServlet.MENU_POS);
        menu.setLinkUri("#");
        WebMenuEntry config = new WebMenuEntry(rbp.getResourceBundle().getString("I18N_CONFIG"));
        config.setLinkUri(UpdateConfigServlet.PATH);
        menu.getChilds().add(config);
        menuReg = ctx.registerService(IWebMenuEntry.class.getName(), menu, null);
    }

    @Invalidate
    public void stop() {
        unregisterServlet();
        if (menuReg != null) {
            menuReg.unregister();
        }
    }

    private void unregisterServlet() {
        if (httpService != null) {
            httpService.unregister(PATH);
        }
    }
}

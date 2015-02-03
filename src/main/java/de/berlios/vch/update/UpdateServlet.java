package de.berlios.vch.update;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.log.LogService;
import org.osgi.service.obr.Capability;
import org.osgi.service.obr.Repository;
import org.osgi.service.obr.RepositoryAdmin;
import org.osgi.service.obr.Requirement;
import org.osgi.service.obr.Resolver;
import org.osgi.service.obr.Resource;

import de.berlios.vch.config.ConfigService;
import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.web.NotifyMessage;
import de.berlios.vch.web.NotifyMessage.TYPE;
import de.berlios.vch.web.TemplateLoader;
import de.berlios.vch.web.menu.IWebMenuEntry;
import de.berlios.vch.web.menu.WebMenuEntry;
import de.berlios.vch.web.servlets.VchHttpServlet;

@Component
@Provides
public class UpdateServlet extends VchHttpServlet implements ObrManager {

    public static final String PATH = "/extensions";

    public static final String STATIC_PATH = PATH + "/static";

    static final int MENU_POS = Integer.MAX_VALUE - 200;

    private List<Resource> availableBundles = new Vector<Resource>();

    private List<BundleRepresentation> installedBundles = new Vector<BundleRepresentation>();

    private final String FELIX_OBR = "http://felix.apache.org/obr/releases.xml";

    private List<String> ignoreList = Arrays.asList(new String[] { "de.berlios.vch.slf4j-logger", "de.berlios.vch.bundle-loader" });

    boolean updateUpdateManager = false;

    @Requires(filter = "(instance.name=vch.web.update)")
    private ResourceBundleProvider rbp;

    @Requires
    private LogService logger;

    @Requires
    private TemplateLoader templateLoader;

    @Requires
    private HttpService httpService;

    @Requires
    private ConfigService cs;
    private Preferences prefs;

    @Requires
    private RepositoryAdmin repoAdmin;

    private BundleContext ctx;

    private ServiceRegistration menuReg;

    public UpdateServlet(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            // if the user has submitted any form, execute the actions
            if (req.getParameter("submit_install") != null) {
                install(req, resp);
            } else if (req.getParameter("submit_uninstall") != null) {
                uninstall(req);
            } else if (req.getParameter("submit_stop") != null) {
                stopBundles(req, resp);
            } else if (req.getParameter("submit_start") != null) {
                startBundles(req, resp);
            } else if (req.getParameter("submit_update") != null) {
                updateBundles(req, resp);
                if (updateUpdateManager) {
                    Thread.sleep(3000); // wait some seconds, so that the update servlet registers again
                    resp.sendRedirect(PATH + "?updated=true");
                    return;
                }
            } else if (req.getParameter("updated") != null) {
                // we have been redirected from an update, which included the update manager
                // now we should add a notify message
                addNotify(req, new NotifyMessage(TYPE.INFO, rbp.getResourceBundle().getString("info.please_restart")));
            }

            // render page parts
            if (req.getParameter("tab") != null) {

                String tab = req.getParameter("tab");
                if ("installed".equalsIgnoreCase(tab)) {
                    updateInstalledList();
                    renderInstalled(req, resp);
                } else if ("available".equalsIgnoreCase(tab)) {
                    updateInstalledList();
                    updateAvailableList();
                    renderAvailable(req, resp);
                }

            } else if (req.getParameter("updates") != null) {

                List<Resource> available = downloadAvailableList();
                for (Iterator<Resource> iterator = available.iterator(); iterator.hasNext();) {
                    Resource resource = iterator.next();
                    if (!isInstalled(resource)) {
                        iterator.remove();
                    } else {
                        for (BundleRepresentation bundle : installedBundles) {
                            if (bundle.getSymbolicName().equals(resource.getSymbolicName())) {
                                if (compare(Version.parseVersion(bundle.getVersion()), resource.getVersion()) > 0) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                }
                resp.setContentType("application/json; charset=utf-8");
                resp.getWriter().write(toJSON(available));
            } else {
                renderMainPage(req, resp);
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private void updateBundles(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String[] bundleIds = req.getParameterValues("installed");
        if (bundleIds == null) {
            addNotify(req, new NotifyMessage(TYPE.ERROR, rbp.getResourceBundle().getString("info.no_extension_selected")));
            return;
        }

        updateUpdateManager = false;
        Resource updateManagerResource = null;

        Resolver resolver = repoAdmin.resolver();
        for (String bundleId : bundleIds) {
            int _bundleId = Integer.parseInt(bundleId);
            Bundle bundle = ctx.getBundle(_bundleId);
            String symbolicName = bundle.getSymbolicName();
            // String version = (String) bundle.getHeaders().get(Constants.BUNDLE_VERSION);
            String filter = "(symbolicname=" + symbolicName + ")";
            logger.log(LogService.LOG_INFO, "Resolving " + filter);
            Collection<Resource> resources = filterByVersion(repoAdmin.discoverResources(filter));
            logger.log(LogService.LOG_INFO, "Found " + resources.size() + " resources");
            if (resources.size() > 0) {
                for (Resource resource : resources) {
                    if (resource.getSymbolicName().equals(ctx.getBundle().getSymbolicName())) {
                        // we are trying to update vch-update. to avoid, that the obr bundle tries to update itself
                        // we have to handle this update in a special manner
                        updateManagerResource = resource;
                        updateUpdateManager = true;
                    } else {
                        if (!ignoreList.contains(resource.getSymbolicName())) {
                            logger.log(LogService.LOG_INFO, "Adding " + resource.getSymbolicName() + " to update list");
                            resolver.add(resource);
                        }
                    }
                }
            }
        }

        if (resolver.resolve()) {
            boolean andStartBundle = true;
            resolver.deploy(andStartBundle); // deploy and start
            addNotify(req, new NotifyMessage(TYPE.INFO, rbp.getResourceBundle().getString("info.please_restart")));
        } else {
            String msg = rbp.getResourceBundle().getString("error.load_list");
            logger.log(LogService.LOG_ERROR, msg);
            addNotify(req, new NotifyMessage(TYPE.ERROR, msg));
        }

        if (updateUpdateManager) {
            updateUpdateManager(req, resp, updateManagerResource);
            return;
        }

        updateInstalledList();
        updateAvailableList();
    }

    private void updateUpdateManager(HttpServletRequest req, HttpServletResponse resp, Resource resource) throws MalformedURLException,
            ServiceUnavailableException, Exception {
        boolean isFelixObrConfigured = false;
        if (getOBRs().contains(FELIX_OBR)) {
            isFelixObrConfigured = true;
            removeOBR(FELIX_OBR);
        }

        Resolver resolver = repoAdmin.resolver();
        resolver.add(resource);
        if (resolver.resolve()) {
            boolean andStartBundle = true;
            resolver.deploy(andStartBundle); // deploy and start
        } else {
            String msg = rbp.getResourceBundle().getString("error.load_list");
            logger.log(LogService.LOG_ERROR, msg);
            addNotify(req, new NotifyMessage(TYPE.ERROR, msg));
        }

        if (isFelixObrConfigured) {
            addOBR(FELIX_OBR);
        }
    }

    private void stopBundles(HttpServletRequest req, HttpServletResponse resp) {
        String[] bundleIds = req.getParameterValues("installed");
        if (bundleIds == null) {
            addNotify(req, new NotifyMessage(TYPE.ERROR, rbp.getResourceBundle().getString("info.no_extension_selected")));
            return;
        }
        for (String bundleId : bundleIds) {
            int _bundleId = Integer.parseInt(bundleId);
            try {
                Bundle bundle = ctx.getBundle(_bundleId);
                if (bundle != null) {
                    bundle.stop();
                } else {
                    String mesg = MessageFormat.format(rbp.getResourceBundle().getString("warning.bundle_does_not_exist"), _bundleId);
                    addNotify(req, new NotifyMessage(TYPE.WARNING, mesg));
                }
            } catch (BundleException e) {
                String msg = MessageFormat.format(rbp.getResourceBundle().getString("error.stop_bundle"), _bundleId);
                logger.log(LogService.LOG_ERROR, msg, e);
                addNotify(req, new NotifyMessage(TYPE.ERROR, msg, e));
            }
        }
    }

    private void startBundles(HttpServletRequest req, HttpServletResponse resp) {
        String[] bundleIds = req.getParameterValues("installed");
        if (bundleIds == null) {
            addNotify(req, new NotifyMessage(TYPE.ERROR, rbp.getResourceBundle().getString("info.no_extension_selected")));
            return;
        }
        for (String bundleId : bundleIds) {
            int _bundleId = Integer.parseInt(bundleId);
            try {
                Bundle bundle = ctx.getBundle(_bundleId);
                if (bundle != null) {
                    bundle.start();
                } else {
                    String mesg = MessageFormat.format(rbp.getResourceBundle().getString("warning.bundle_does_not_exist"), _bundleId);
                    addNotify(req, new NotifyMessage(TYPE.WARNING, mesg));
                }
            } catch (BundleException e) {
                String msg = MessageFormat.format(rbp.getResourceBundle().getString("error.start_bundle"), _bundleId);
                logger.log(LogService.LOG_ERROR, msg, e);
                addNotify(req, new NotifyMessage(TYPE.ERROR, msg, e));
            }
        }
    }

    private void renderAvailable(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> tplParams = new HashMap<String, Object>();
        List<ResourceRepresentation> list = new ArrayList<ResourceRepresentation>(availableBundles.size());
        for (Resource resource : availableBundles) {
            list.add(new ResourceRepresentation(resource));
        }
        tplParams.put("ACTION", PATH);
        tplParams.put("AVAILABLE", list);
        String template = templateLoader.loadTemplate("extensions_available.ftl", tplParams);
        resp.getWriter().println(template);
    }

    private void renderInstalled(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> tplParams = new HashMap<String, Object>();
        tplParams.put("ACTION", PATH);
        tplParams.put("INSTALLED", installedBundles);
        String template = templateLoader.loadTemplate("extensions_installed.ftl", tplParams);
        resp.getWriter().println(template);
    }

    private void renderMainPage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> tplParams = new HashMap<String, Object>();
        String path = req.getRequestURI();
        tplParams.put("ACTION", path);
        tplParams.put("STATIC_PATH", STATIC_PATH);
        tplParams.put("TITLE", rbp.getResourceBundle().getString("I18N_EXTENSIONS"));

        // add additional css
        List<String> css = new ArrayList<String>();
        css.add(STATIC_PATH + "/extensions.css");
        tplParams.put("CSS_INCLUDES", css);

        // add errors and messages
        tplParams.put("NOTIFY_MESSAGES", getNotifyMessages(req));

        tplParams.put("AVAILABLE", availableBundles);
        tplParams.put("INSTALLED", installedBundles);
        String template = templateLoader.loadTemplate("extensions.ftl", tplParams);
        resp.getWriter().println(template);
    }

    private void uninstall(HttpServletRequest req) {
        String[] bundleIds = req.getParameterValues("installed");
        if (bundleIds == null) {
            addNotify(req, new NotifyMessage(TYPE.ERROR, rbp.getResourceBundle().getString("info.no_extension_selected")));
            return;
        }
        for (String bundleId : bundleIds) {
            long id = Long.parseLong(bundleId);
            Bundle bundle = ctx.getBundle(id);
            if (bundle != null) {
                try {
                    bundle.uninstall();
                } catch (BundleException e) {
                    String msg = rbp.getResourceBundle().getString("error.uninstall_extension");
                    logger.log(LogService.LOG_ERROR, msg, e);
                    addNotify(req, new NotifyMessage(TYPE.ERROR, msg, e));
                }
            }
        }
    }

    private void install(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        // resolve given symbolicNames to Resource objects and pass them to the Resolver
        List<Resource> available = downloadAvailableList();
        String[] symbolicNames = req.getParameterValues("available");
        if (symbolicNames == null) {
            addNotify(req, new NotifyMessage(TYPE.ERROR, rbp.getResourceBundle().getString("info.no_extension_selected")));
            return;
        }

        Resolver resolver = repoAdmin.resolver();
        for (String symbolicName : symbolicNames) {
            for (Resource resource : available) {
                if (resource.getSymbolicName().equals(symbolicName)) {
                    resolver.add(resource);
                }
            }
        }

        // try to resolve the bundles and then install them
        if (resolver.resolve()) {
            resolver.deploy(true);
            addNotify(req, new NotifyMessage(TYPE.INFO, rbp.getResourceBundle().getString("info.please_restart")));
        } else {
            for (Requirement requirement : resolver.getUnsatisfiedRequirements()) {
                String msg = "Unsatisfied requirement: " + requirement.getName() + " " + requirement.getFilter();
                logger.log(LogService.LOG_WARNING, msg);
                addNotify(req, new NotifyMessage(TYPE.WARNING, msg));
            }
        }
    }

    private List<Resource> downloadAvailableList() throws MalformedURLException, Exception {
        // add repos from configuration
        for (String uri : getOBRs()) {
            try {
                logger.log(LogService.LOG_DEBUG, "Adding bundle repository " + uri);
                repoAdmin.addRepository(new URL(uri));
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Couldn't add repository", e);
            }
        }

        Repository[] repos = repoAdmin.listRepositories();
        StringBuilder sb = new StringBuilder();
        for (Repository repo : repos) {
            sb.append('\n').append(repo.getName()).append(' ').append(repo.getURL().toString());
        }
        logger.log(LogService.LOG_INFO, "Loading extensions list from obrs:" + sb.toString());
        String filter = "(symbolicname=*)"; // get all bundles
        logger.log(LogService.LOG_INFO, "Resolving " + filter);
        Resource[] res = repoAdmin.discoverResources(filter);
        Collection<Resource> resources = filterByVersion(res);
        for (Iterator<Resource> iterator = resources.iterator(); iterator.hasNext();) {
            Resource resource = iterator.next();
            Capability[] caps = resource.getCapabilities();
            Capability capability = getCapability(caps, "vch");
            if (capability != null) {
                if (!"true".equals(capability.getProperties().get("bundle"))) {
                    iterator.remove();
                }
            } else {
                iterator.remove();
            }
        }
        logger.log(LogService.LOG_INFO, "Found " + resources.size() + " resources");
        List<Resource> allResources = new ArrayList<Resource>();
        allResources.addAll(resources);
        Collections.sort(allResources, new ResourceNameComparator());
        return allResources;
    }

    private class ResourceNameComparator implements Comparator<Resource> {
        @Override
        public int compare(Resource r1, Resource r2) {
            return r1.getPresentationName().compareTo(r2.getPresentationName());
        }
    }

    /**
     * Filters all old version, so that only the newest versions of a plugin are in the returned collection
     * 
     * @param res
     * @return
     */
    private Collection<Resource> filterByVersion(Resource[] res) {
        Map<String, Resource> filterMap = new HashMap<String, Resource>();
        for (int i = 0; i < res.length; i++) {
            if (filterMap.containsKey(res[i].getSymbolicName())) {
                // we filter the list of available bundles. only the newest
                // bundles are included
                Resource r1 = filterMap.get(res[i].getSymbolicName());
                Resource r2 = res[i];
                if (compare(r1.getVersion(), r2.getVersion()) <= 0) {
                    logger.log(LogService.LOG_DEBUG, "Bundle " + r1.getSymbolicName() + " with version " + r1.getVersion() + " will be dropped");
                    logger.log(LogService.LOG_DEBUG, "Adding " + res[i].getPresentationName() + " with version " + res[i].getVersion());
                    filterMap.put(res[i].getSymbolicName(), r2);
                }
            } else {
                logger.log(LogService.LOG_DEBUG, "Adding " + res[i].getPresentationName() + " with version " + res[i].getVersion());
                filterMap.put(res[i].getSymbolicName(), res[i]);
            }
        }
        return filterMap.values();
    }

    private Capability getCapability(Capability[] caps, String name) {
        for (Capability capability : caps) {
            if (name.equals(capability.getName())) {
                return capability;
            }
        }
        return null;
    }

    private class BundleNameComparator implements Comparator<BundleRepresentation> {
        @Override
        public int compare(BundleRepresentation b1, BundleRepresentation b2) {
            return b1.getName().toLowerCase().compareTo(b2.getName().toLowerCase());
        }
    }

    private String getBundleName(Bundle bundle) {
        // Get the bundle name or location.
        String name = (String) bundle.getHeaders().get(Constants.BUNDLE_NAME);
        // If there is no name, then default to symbolic name.
        name = (name == null) ? bundle.getSymbolicName() : name;
        // If there is no symbolic name, resort to location.
        name = (name == null) ? bundle.getLocation() : name;
        return name;
    }

    private synchronized void updateInstalledList() {
        installedBundles.clear();
        Bundle[] bundles = ctx.getBundles();
        for (Bundle bundle : bundles) {
            if ("true".equalsIgnoreCase((String) bundle.getHeaders().get("VCH-Bundle")) && !ignoreList.contains(bundle.getSymbolicName())) {
                BundleRepresentation br = new BundleRepresentation();
                br.setBundleId(bundle.getBundleId());
                br.setName(getBundleName(bundle));
                br.setSymbolicName(bundle.getSymbolicName());
                br.setAuthor((String) bundle.getHeaders().get(Constants.BUNDLE_VENDOR));
                br.setDescription((String) bundle.getHeaders().get(Constants.BUNDLE_DESCRIPTION));
                String version = (String) bundle.getHeaders().get(Constants.BUNDLE_VERSION);
                br.setVersion(version);
                br.setState(bundle.getState());
                installedBundles.add(br);
            }
        }
        Collections.sort(installedBundles, new BundleNameComparator());
    }

    private synchronized void updateAvailableList() throws MalformedURLException, Exception {
        availableBundles.clear();
        availableBundles.addAll(downloadAvailableList());
        for (Iterator<Resource> iterator = availableBundles.iterator(); iterator.hasNext();) {
            Resource res = iterator.next();
            if (isInstalled(res) || ignoreList.contains(res.getSymbolicName())) {
                iterator.remove();
            }
        }
    }

    private boolean isInstalled(Resource resource) {
        for (Iterator<BundleRepresentation> iterator = installedBundles.iterator(); iterator.hasNext();) {
            BundleRepresentation current = iterator.next();
            if (current.getSymbolicName().equals(resource.getSymbolicName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        get(req, resp);
    }

    private String toJSON(Resource res) {
        Map<String, Object> object = new HashMap<String, Object>();
        object.put("symbolicName", res.getSymbolicName());
        object.put("version", res.getVersion().toString());
        return new JSONObject(object).toString();
    }

    private String toJSON(List<Resource> resources) {
        if (!resources.isEmpty()) {
            String json = "[";
            for (Iterator<Resource> iterator = resources.iterator(); iterator.hasNext();) {
                Resource resource = iterator.next();
                json += toJSON(resource);
                if (iterator.hasNext()) {
                    json += ", ";
                }
            }
            return json += "]";
        } else {
            return "[]";
        }
    }

    @Override
    public List<String> getOBRs() throws MalformedURLException, Exception {
        List<String> obrUris = new ArrayList<String>();
        try {
            Preferences persitentRepos = prefs.node("obrs");
            String[] obrs = persitentRepos.childrenNames();
            for (String id : obrs) {
                Preferences obr = persitentRepos.node(id);
                String uri = obr.get("uri", "");
                obrUris.add(uri);
            }
        } catch (BackingStoreException e) {
            logger.log(LogService.LOG_ERROR, "Couldn't load preferences", e);
        }
        Collections.sort(obrUris);
        if (obrUris.isEmpty()) {
            addOBR("http://hampelratte.org/maven/repository.xml");
            addOBR(FELIX_OBR);
            obrUris.add("http://hampelratte.org/maven/repository.xml");
            obrUris.add(FELIX_OBR);
        }
        return obrUris;
    }

    @Override
    public void addOBR(String uri) throws MalformedURLException, Exception {
        repoAdmin.addRepository(new URL(uri));

        Preferences obrs = prefs.node("obrs");
        String id = UUID.randomUUID().toString();
        Preferences obr = obrs.node(id);
        obr.put("uri", uri);
    }

    @Override
    public void removeOBR(String uri) throws MalformedURLException, ServiceUnavailableException {
        ServiceReference sr = ctx.getServiceReference(RepositoryAdmin.class.getName());
        if (sr == null) {
            throw new ServiceUnavailableException(rbp.getResourceBundle().getString("error.obr_not_available"));
        }
        RepositoryAdmin adm = (RepositoryAdmin) ctx.getService(sr);

        Preferences obrs = prefs.node("obrs");
        try {
            for (String key : obrs.childrenNames()) {
                Preferences obr = obrs.node(key);
                if (uri.equals(obr.get("uri", ""))) {
                    obr.removeNode();
                    adm.removeRepository(new URL(uri));
                }
            }
        } catch (BackingStoreException e) {
            logger.log(LogService.LOG_ERROR, "Couldn't remove obr", e);
        }
    }

    public static int compare(Version v1, Version v2) {
        if (v1 == v2) {
            return 0;
        }

        int result = v1.getMajor() - v2.getMajor();
        if (result != 0) {
            return result;
        }
        result = v1.getMinor() - v2.getMinor();
        if (result != 0) {
            return result;
        }
        result = v1.getMicro() - v2.getMicro();
        if (result != 0) {
            return result;
        } else {
            // compare the qualifier
            if (v1.getQualifier().length() > 0 && v2.getQualifier().length() == 0) {
                return -1;
            } else if (v2.getQualifier().length() > 0 && v1.getQualifier().length() == 0) {
                return 1;
            } else if (v1.getQualifier().length() > 0 && v2.getQualifier().length() > 0) {
                return v1.getQualifier().compareTo(v2.getQualifier());
            } else {
                return 0;
            }
        }

    }

    protected void error(HttpServletResponse res, int code, String msg) throws IOException {
        error(res, code, msg, null, false);
    }

    protected void error(HttpServletResponse res, int code, String msg, boolean isAjaxRequest) throws IOException {
        error(res, code, msg, null, isAjaxRequest);
    }

    protected void error(HttpServletResponse res, int code, String msg, Throwable t, boolean isAjaxRequest) throws IOException {
        if (isAjaxRequest) {
            res.setHeader("Content-Type", "text/plain; charset=utf-8");
            res.setStatus(code);
            res.getWriter().println(msg + "\n");
            if (t != null) {
                t.printStackTrace(res.getWriter());
            }
        } else {
            Map<String, Object> tplParams = new HashMap<String, Object>();
            tplParams.put("TITLE", rbp.getResourceBundle().getString("I18N_ERROR"));
            tplParams.put("MESSAGE", msg != null ? msg : "");

            if (t != null) {
                tplParams.put("STACKTRACE", NotifyMessage.stackTraceToString(t));
            }

            res.setHeader("Content-Type", "text/html;charset=utf-8");
            res.setStatus(code);
            String template = templateLoader.loadTemplate("error.ftl", tplParams);
            res.getWriter().println(template);
        }
    }

    @Validate
    public void start() {
        prefs = cs.getUserPreferences("");

        try {
            registerServlet();
        } catch (Exception e) {
            logger.log(LogService.LOG_ERROR, "Couldn't register update servlet", e);
        }
    }

    private void registerServlet() throws ServletException, NamespaceException {
        // register the extensions servlet
        httpService.registerServlet(PATH, this, null, null);

        // register web interface menu
        WebMenuEntry menu = new WebMenuEntry();
        menu.setTitle(rbp.getResourceBundle().getString("I18N_EXTENSIONS"));
        menu.setPreferredPosition(MENU_POS);
        menu.setLinkUri("#");
        WebMenuEntry content = new WebMenuEntry(rbp.getResourceBundle().getString("I18N_MANAGE"));
        content.setLinkUri(UpdateServlet.PATH);
        menu.getChilds().add(content);
        menuReg = ctx.registerService(IWebMenuEntry.class.getName(), menu, null);
    }

    @Invalidate
    public void stop() {
        prefs = null;

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

package de.berlios.vch.update;

import org.osgi.framework.Version;
import org.osgi.service.obr.Capability;
import org.osgi.service.obr.Resource;

public class ResourceRepresentation {
    private Resource resource;

    public ResourceRepresentation(Resource resource) {
        this.resource = resource;
    }

    public String getPresentationName() {
        return resource.getPresentationName();
    }

    public String getSymbolicName() {
        return resource.getSymbolicName();
    }

    public Version getVersion() {
        return resource.getVersion();
    }
    
    public String getDescription() {
        String desc = (String) resource.getProperties().get(Resource.DESCRIPTION);
        return desc != null ? desc : "N/A";
    }
    
    public String getAuthor() {
        String author = "N/A";
        for (Capability capability : resource.getCapabilities()) {
            if("vch".equals(capability.getName())) {
                if (capability.getProperties().get("Bundle-Vendor") != null) {
                    author = (String) capability.getProperties().get("Bundle-Vendor");
                }
            }
        }
        return author;
    }
}

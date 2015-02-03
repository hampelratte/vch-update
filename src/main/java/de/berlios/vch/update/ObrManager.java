package de.berlios.vch.update;

import java.net.MalformedURLException;
import java.util.List;

public interface ObrManager {
    public void addOBR(String uri) throws MalformedURLException, Exception;
    public void removeOBR(String uri) throws MalformedURLException, ServiceUnavailableException;
    public List<String> getOBRs() throws MalformedURLException, Exception;
}

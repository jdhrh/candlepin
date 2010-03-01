/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.guice;

import org.fedoraproject.candlepin.config.Config;

import com.google.inject.Module;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * CustomizableModules
 */
public class CustomizableModules {
    
    public static final String MODULE_CONFIG_PREFIX = "module.config";
    
    /**
     * @return returns the set of modules to use.
     */
    public Set<Module> load() {
        Map<String, String> loaded =
            configuration().configurationWithPrefix(MODULE_CONFIG_PREFIX);

        return customizedConfiguration(loaded);
    }
    
    /**
     * Reads the given configuration and returns the set of modules.
     * @param loadedConfiguration configuration to parse.
     * @return Set of configured modules.
     */
    @SuppressWarnings("unchecked")
    public Set<Module> customizedConfiguration(Map<String, String> loadedConfiguration) {
        try {
            Set toReturn = new HashSet();
            
            for (String guiceModuleName : loadedConfiguration.keySet()) {
                toReturn.add(Class.forName(
                        loadedConfiguration.get(guiceModuleName)).newInstance());
            }
            
            return toReturn; 
        }
        catch (Exception e) {
            throw new RuntimeException("Exception when instantiation guice module.", e);
        }
    }

    /**
     * Load custom configuration from the given input stream.
     * @param input Stream containing configuration information.
     * @return Map of the configuration.
     * @throws IOException thrown if there is a problem reading the stream.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> loadCustomConfiguration(InputStream input)
        throws IOException {

        Properties loaded = new Properties();
        loaded.load(input);
        return new HashMap(loaded);
    }
    
    protected Config configuration() {
        return new Config();
    }
}

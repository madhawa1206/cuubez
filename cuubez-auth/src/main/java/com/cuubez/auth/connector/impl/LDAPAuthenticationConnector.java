/**
 *	Copyright [2013] [www.cuubez.com]
 *	Licensed under the Apache License, Version 2.0 (the "License");
 *	you may not use this file except in compliance with the License.
 *	You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS,
 *	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *	See the License for the specific language governing permissions and
 *	limitations under the License.
 */

package com.cuubez.auth.connector.impl;

import java.util.Hashtable;
import java.util.UUID;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.cuubez.auth.connector.AuthenticationConnector;
import com.cuubez.auth.exception.AuthenticationConnectorException;
import com.cuubez.auth.model.AuthenticationCode;
import com.cuubez.auth.model.AuthenticationToken;

/**
 * @author ruwan
 */
public class LDAPAuthenticationConnector implements AuthenticationConnector {

    private static Log log = LogFactory.getLog(LDAPAuthenticationConnector.class);

    private static LDAPAuthenticationConnector ldapAuthenticationConnector;

    private Hashtable<Object, Object> env = null;

    private String host = "localhost";
    private String port = "10389";
    private String partitionName = "o=ucsc,dc=lk";
    private String authType = "simple";

    /**
	 * 
	 */
    private LDAPAuthenticationConnector() {
        env = new Hashtable<Object, Object>();

        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port + "/" + partitionName);
        env.put(Context.SECURITY_AUTHENTICATION, authType);
    }

    /*
     * (non-Javadoc)
     * @see com.cuubez.auth.connector.AuthenticationConnector#authenticate(com.cuubez.auth.model.
     * AuthenticationCode)
     */
    public AuthenticationToken authenticate(AuthenticationCode authenticationCode)
            throws AuthenticationConnectorException {
        AuthenticationToken authenticationToken = null;

        if (authenticationCode.getPrincipal() == null || authenticationCode.getCredentials() == null) {
            String errMsg = "LDAP authentication failed. - Principal :" + authenticationCode.getPrincipal()
                    + " Credentials :" + authenticationCode.getCredentials();
            log.error(errMsg);
            throw new AuthenticationConnectorException(errMsg);
        }

        env.put(Context.SECURITY_PRINCIPAL, authenticationCode.getPrincipal());
        env.put(Context.SECURITY_CREDENTIALS, authenticationCode.getCredentials());

        try {
            InitialDirContext ctx = new InitialDirContext(env);
            if (log.isDebugEnabled()) {
                log.debug("(" + authenticationCode.getPrincipal() + ") Authenticated.");
            }
            authenticationToken = new AuthenticationToken(UUID.randomUUID().toString());
            ctx.close();
        } catch (NamingException e) {
            String errMsg = "LDAP authentication failed. - " + e.getMessage();
            log.error(errMsg, e);
            throw new AuthenticationConnectorException(errMsg, e);
        }

        return authenticationToken;
    }

    /**
     * @return
     */
    public static LDAPAuthenticationConnector getInstance() {
        if (ldapAuthenticationConnector == null) {
            ldapAuthenticationConnector = new LDAPAuthenticationConnector();
        }
        return ldapAuthenticationConnector;
    }

    public String getRole(String principal) throws AuthenticationConnectorException {
        String roleId = null;

        if (principal == null) {
            String errMsg = "LDAP role retrieval failed. - Principal :" + principal;
            log.error(errMsg);
            throw new AuthenticationConnectorException(errMsg);
        }

        try {
            InitialDirContext ctx = new InitialDirContext(env);

            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            String searchFilter = "(objectClass=groupOfUniqueNames)";
            String returnedAtts[] = { "uniquemember", "cn" };
            searchCtls.setReturningAttributes(returnedAtts);
            String searchLocation = "ou=groups";
            NamingEnumeration answer = ctx.search(searchLocation, searchFilter, searchCtls);

            while (answer.hasMoreElements()) {
                SearchResult result = (SearchResult) answer.nextElement();
                Attributes attributes = result.getAttributes();
                Attribute attribute = attributes.get("uniquemember");
                NamingEnumeration ne = attribute.getAll();
                while (ne.hasMore()) {
                    if (principal.equals(ne.next().toString())) {
                        attribute = attributes.get("cn");
                        roleId = (String) attribute.get();
                    }
                }
            }

            ctx.close();
            return "cn=" + roleId + "," + searchLocation + "," + partitionName;
        } catch (NamingException e) {
            String errMsg = "LDAP role retrieval failed. - " + e.getMessage();
            log.error(errMsg, e);
            throw new AuthenticationConnectorException(errMsg, e);
        }
    }
}

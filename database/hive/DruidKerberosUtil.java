/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.druid.security;

import org.apache.hadoop.security.authentication.client.AuthenticatedURL;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.util.KerberosUtil;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utils class for Druid Kerberos stuff.
 */
public final class DruidKerberosUtil {
  protected static final Logger LOG = LoggerFactory.getLogger(DruidKerberosUtil.class);
  private static final ReentrantLock KERBEROS_LOCK = new ReentrantLock(true);

  private DruidKerberosUtil() {
  }

  /**
   * This method always needs to be called within a doAs block so that the client's TGT credentials
   * can be read from the Subject.
   *
   * @return Kerberos Challenge String.
   *
   * @throws AuthenticationException on authentication errors.
   */

  static String kerberosChallenge(String server) throws AuthenticationException {
    KERBEROS_LOCK.lock();
    try {
      // This Oid for Kerberos GSS-API mechanism.
      Oid mechOid = KerberosUtil.getOidInstance("GSS_KRB5_MECH_OID");
      GSSManager manager = GSSManager.getInstance();
      // GSS name for server
      GSSName serverName = manager.createName("HTTP@" + server, GSSName.NT_HOSTBASED_SERVICE);
      // Create a GSSContext for authentication with the service.
      // We're passing client credentials as null since we want them to be read from the Subject.
      GSSContext
          gssContext =
          manager.createContext(serverName.canonicalize(mechOid), mechOid, null, GSSContext.DEFAULT_LIFETIME);
      gssContext.requestMutualAuth(true);
      gssContext.requestCredDeleg(true);
      // Establish context
      byte[] inToken = new byte[0];
      byte[] outToken = gssContext.initSecContext(inToken, 0, inToken.length);
      gssContext.dispose();
      // Base64 encoded and stringified token for server
      LOG.debug("Got valid challenge for host {}", serverName);
      return Base64.getEncoder().encodeToString(outToken);
    } catch (GSSException | IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
      throw new AuthenticationException(e);
    } finally {
//      KERBEROS_LOCK.unlock();
    }
  }

  static HttpCookie getAuthCookie(CookieStore cookieStore, URI uri) {
    if (cookieStore == null) {
      return null;
    }
    boolean isSSL = uri.getScheme().equals("https");
    List<HttpCookie> cookies = cookieStore.getCookies();

    for (HttpCookie c : cookies) {
      // If this is a secured cookie and the current connection is non-secured,
      // then, skip this cookie. We need to skip this cookie because, the cookie
      // replay will not be transmitted to the server.
      if (c.getSecure() && !isSSL) {
        continue;
      }
      if (c.getName().equals(AuthenticatedURL.AUTH_COOKIE)) {
        return c;
      }
    }
    return null;
  }

  static void removeAuthCookie(CookieStore cookieStore, URI uri) {
    HttpCookie authCookie = getAuthCookie(cookieStore, uri);
    if (authCookie != null) {
      cookieStore.remove(uri, authCookie);
    }
  }

  static boolean needToSendCredentials(CookieStore cookieStore, URI uri) {
    return getAuthCookie(cookieStore, uri) == null;
  }

}

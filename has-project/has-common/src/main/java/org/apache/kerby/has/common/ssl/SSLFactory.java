/**
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
package org.apache.kerby.has.common.ssl;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.kerby.has.common.HasConfig;
import org.apache.kerby.has.common.HasException;
import org.apache.kerby.has.common.util.ConnectionConfigurator;
import org.apache.kerby.has.common.util.StringUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;

import static org.apache.kerby.has.common.util.PlatformName.IBM_JAVA;

/**
 * Borrow the class from Apache Hadoop
 */

/**
 * Factory that creates SSLEngine and SSLSocketFactory instances using
 * Hadoop configuration information.
 *
 * which reloads public keys if the truststore file changes.
 *
 * This factory is used to configure HTTPS in Hadoop HTTP based endpoints, both
 * client and server.
 */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public class SSLFactory implements ConnectionConfigurator {

  @InterfaceAudience.Private
  public enum Mode {
    CLIENT, SERVER
  }

  public static final String SSL_REQUIRE_CLIENT_CERT_KEY =
    "hadoop.ssl.require.client.CERT";
  public static final String SSL_HOSTNAME_VERIFIER_KEY =
    "hadoop.ssl.hostname.verifier";
  public static final String SSL_CLIENT_CONF_KEY =
    "hadoop.ssl.client.conf";
  public static final String SSL_SERVER_CONF_KEY =
      "hadoop.ssl.server.conf";
  public static final String SSLCERTIFICATE = IBM_JAVA ? "ibmX509" : "SunX509";

  public static final boolean DEFAULT_SSL_REQUIRE_CLIENT_CERT = false;

  public static final String KEYSTORES_FACTORY_CLASS_KEY =
    "hadoop.ssl.keystores.factory.class";

  public static final String SSL_ENABLED_PROTOCOLS =
      "hadoop.ssl.enabled.protocols";
  public static final String DEFAULT_SSL_ENABLED_PROTOCOLS = "TLSv1";

  private HasConfig conf;
  private Mode mode;
  private boolean requireClientCert;
  private SSLContext context;
  private HostnameVerifier hostnameVerifier;
  private KeyStoresFactory keystoresFactory;

  private String[] enabledProtocols = null;

  /**
   * Creates an SSLFactory.
   *
   * @param mode SSLFactory mode, client or server.
   * @param conf Hadoop configuration from where the SSLFactory configuration
   * will be read.
   * @throws HasException thrown if an HAS error happened.
   */
  public SSLFactory(Mode mode, HasConfig conf) throws HasException {
    this.conf = conf;
    if (mode == null) {
      throw new IllegalArgumentException("mode cannot be NULL");
    }
    this.mode = mode;
    requireClientCert = conf.getBoolean(SSL_REQUIRE_CLIENT_CERT_KEY,
                                        DEFAULT_SSL_REQUIRE_CLIENT_CERT);
    HasConfig sslConf = readSSLConfiguration(mode);

    keystoresFactory = new KeyStoresFactory();
    keystoresFactory.setConf(sslConf);

    enabledProtocols = new String[] {DEFAULT_SSL_ENABLED_PROTOCOLS};
  }

  private HasConfig readSSLConfiguration(Mode mode) throws HasException {
    HasConfig sslConf = new HasConfig();
    sslConf.setBoolean(SSL_REQUIRE_CLIENT_CERT_KEY, requireClientCert);
    String sslConfResource;
    if (mode == Mode.CLIENT) {
      sslConfResource = conf.getString(SSLFactory.SSL_CLIENT_CONF_KEY);
    } else {
      sslConfResource = conf.getString(SSLFactory.SSL_SERVER_CONF_KEY);
    }
    try {
      sslConf.addIniConfig(new File(sslConfResource));
    } catch (IOException e) {
      throw new HasException(e);
    }
    return sslConf;
  }

  /**
   * Initializes the factory.
   *
   * @throws GeneralSecurityException thrown if an SSL initialization error
   * happened.
   * @throws IOException thrown if an IO error happened while reading the SSL
   * configuration.
   */
  public void init() throws GeneralSecurityException, IOException {
    keystoresFactory.init(mode);
    context = SSLContext.getInstance("TLS");
    context.init(keystoresFactory.getKeyManagers(),
                 keystoresFactory.getTrustManagers(), null);
    context.getDefaultSSLParameters().setProtocols(enabledProtocols);
    hostnameVerifier = getHostnameVerifier(conf);
  }

  private HostnameVerifier getHostnameVerifier(HasConfig conf)
      throws GeneralSecurityException, IOException {
    return getHostnameVerifier(StringUtils.toUpperCase(
        conf.getString(SSL_HOSTNAME_VERIFIER_KEY, "DEFAULT").trim()));
  }

  public static HostnameVerifier getHostnameVerifier(String verifier)
    throws GeneralSecurityException, IOException {
    HostnameVerifier hostnameVerifier;
    if (verifier.equals("DEFAULT")) {
      hostnameVerifier = SSLHostnameVerifier.DEFAULT;
    } else if (verifier.equals("DEFAULT_AND_LOCALHOST")) {
      hostnameVerifier = SSLHostnameVerifier.DEFAULT_AND_LOCALHOST;
    } else if (verifier.equals("STRICT")) {
      hostnameVerifier = SSLHostnameVerifier.STRICT;
    } else if (verifier.equals("STRICT_IE6")) {
      hostnameVerifier = SSLHostnameVerifier.STRICT_IE6;
    } else if (verifier.equals("ALLOW_ALL")) {
      hostnameVerifier = SSLHostnameVerifier.ALLOW_ALL;
    } else {
      throw new GeneralSecurityException("Invalid hostname verifier: "
          + verifier);
    }
    return hostnameVerifier;
  }

  /**
   * Releases any resources being used.
   */
  public void destroy() {
    keystoresFactory.destroy();
  }
  /**
   * Returns the SSLFactory KeyStoresFactory instance.
   *
   * @return the SSLFactory KeyStoresFactory instance.
   */
  public KeyStoresFactory getKeystoresFactory() {
    return keystoresFactory;
  }

  /**
   * Returns a configured SSLEngine.
   *
   * @return the configured SSLEngine.
   * @throws GeneralSecurityException thrown if the SSL engine could not
   * be initialized.
   * @throws IOException thrown if and IO error occurred while loading
   * the server keystore.
   */
  public SSLEngine createSSLEngine()
    throws GeneralSecurityException, IOException {
    SSLEngine sslEngine = context.createSSLEngine();
    if (mode == Mode.CLIENT) {
      sslEngine.setUseClientMode(true);
    } else {
      sslEngine.setUseClientMode(false);
      sslEngine.setNeedClientAuth(requireClientCert);
    }
    sslEngine.setEnabledProtocols(enabledProtocols);
    return sslEngine;
  }

  /**
   * Returns a configured SSLServerSocketFactory.
   *
   * @return the configured SSLSocketFactory.
   * @throws GeneralSecurityException thrown if the SSLSocketFactory could not
   * be initialized.
   * @throws IOException thrown if and IO error occurred while loading
   * the server keystore.
   */
  public SSLServerSocketFactory createSSLServerSocketFactory()
    throws GeneralSecurityException, IOException {
    if (mode != Mode.SERVER) {
      throw new IllegalStateException("Factory is in CLIENT mode");
    }
    return context.getServerSocketFactory();
  }

  /**
   * Returns a configured SSLSocketFactory.
   *
   * @return the configured SSLSocketFactory.
   * @throws GeneralSecurityException thrown if the SSLSocketFactory could not
   * be initialized.
   * @throws IOException thrown if and IO error occurred while loading
   * the server keystore.
   */
  public SSLSocketFactory createSSLSocketFactory()
    throws GeneralSecurityException, IOException {
    if (mode != Mode.CLIENT) {
      throw new IllegalStateException("Factory is in CLIENT mode");
    }
    return context.getSocketFactory();
  }

  /**
   * Returns the hostname verifier it should be used in HttpsURLConnections.
   *
   * @return the hostname verifier.
   */
  public HostnameVerifier getHostnameVerifier() {
    if (mode != Mode.CLIENT) {
      throw new IllegalStateException("Factory is in CLIENT mode");
    }
    return hostnameVerifier;
  }

  /**
   * Returns if client certificates are required or not.
   *
   * @return if client certificates are required or not.
   */
  public boolean isClientCertRequired() {
    return requireClientCert;
  }

  /**
   * If the given {@link HttpURLConnection} is an {@link HttpsURLConnection}
   * configures the connection with the {@link SSLSocketFactory} and
   * {@link HostnameVerifier} of this SSLFactory, otherwise does nothing.
   *
   * @param conn the {@link HttpURLConnection} instance to configure.
   * @return the configured {@link HttpURLConnection} instance.
   *
   * @throws IOException if an IO error occurred.
   */
  @Override
  public HttpURLConnection configure(HttpURLConnection conn)
    throws IOException {
    if (conn instanceof HttpsURLConnection) {
      HttpsURLConnection sslConn = (HttpsURLConnection) conn;
      try {
        sslConn.setSSLSocketFactory(createSSLSocketFactory());
      } catch (GeneralSecurityException ex) {
        throw new IOException(ex);
      }
      sslConn.setHostnameVerifier(getHostnameVerifier());
      conn = sslConn;
    }
    return conn;
  }
}

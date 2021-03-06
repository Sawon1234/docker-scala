package com.q31.dockerscala

import com.typesafe.config.{ConfigFactory, Config}
import org.glassfish.jersey.client.{ClientProperties, ClientConfig}
import org.glassfish.jersey.{SslConfigurator, CommonProperties}
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider
import javax.ws.rs.client.ClientBuilder
import java.security.{KeyStore, Security}
import com.q31.dockerscala.api.DockerClientException
import com.q31.dockerscala.util.{CertificateUtils, JsonClientFilter, DockerClientResponseFilter, DockerClientConfig}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import scala.util.{Failure, Success}

/**
 * @author Joe San (codeintheopen@gmail.com)
 */
object DockerRemoteClientFactory {

  // TODO... DockerClientContext needs WebTarget
  private lazy val clientContext: DockerClientConfig => DockerClientContext = dockerClientConfig => {
    new DockerClientContext(init(dockerClientConfig))
  }

  def buildFromConfig(config: Config): DockerRemoteClient = new DockerRemoteClientImpl(clientContext(DockerClientConfig.withConfig(config)))

  def buildDefault(): DockerRemoteClient = new DockerRemoteClientImpl(clientContext(DockerClientConfig.default()))

  private def init(dockerClientConfig: DockerClientConfig) = {

    val clientConfig = new ClientConfig()
    clientConfig.property(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true)
    clientConfig.property(ClientProperties.READ_TIMEOUT, dockerClientConfig.readTimeOut)

    clientConfig.register(classOf[DockerClientResponseFilter])
    clientConfig.register(classOf[JsonClientFilter])
    clientConfig.register(classOf[JacksonJsonProvider])

    val clientBuilder = ClientBuilder.newBuilder().withConfig(clientConfig)

    val dockerCertPath = dockerClientConfig.dockerCertPath

    if (dockerCertPath != null && CertificateUtils.verifyCertificatesExist(dockerCertPath)) {
      Security.addProvider(new BouncyCastleProvider())
      val sslConfig = SslConfigurator.newInstance(true)

      val httpProtocols = System.getProperty("https.protocols")
      System.setProperty("https.protocols", "TLSv1")
      if (httpProtocols != null) System.setProperty("https.protocols", httpProtocols)

      CertificateUtils.createKeyStore(dockerCertPath) match {
        case Success(suck) => {
          sslConfig.keyStore(suck)
          sslConfig.keyStorePassword("docker")
        }
        case Failure(fuck) => // TODO... Throw exception!
      }
      CertificateUtils.createTrustStore(dockerCertPath) match {
        case Success(suck) => sslConfig.trustStore(suck)
        case Failure(fuck) => // TODO... throw exception
      }

      clientBuilder.sslContext(sslConfig.createSSLContext())
    }
    val webResource = clientBuilder.build().target(dockerClientConfig.uri)

    if (dockerClientConfig.version.isEmpty) webResource
    else webResource.path("v" + dockerClientConfig.version)
  }
}

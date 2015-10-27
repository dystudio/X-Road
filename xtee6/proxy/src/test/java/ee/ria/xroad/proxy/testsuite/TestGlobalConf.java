package ee.ria.xroad.proxy.testsuite;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.EjbcaSignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

/**
 * Test globalconf implementation.
 */
public class TestGlobalConf extends EmptyGlobalConf {

    @Override
    public String getInstanceIdentifier() {
        return "EE";
    }

    @Override
    public Collection<String> getProviderAddress(ClientId provider) {
        if (currentTestCase() == null || provider == null) {
            return singleton("http://127.0.0.1:"
                    + SystemProperties.getServerProxyPort());
        }

        String addr = currentTestCase().getProviderAddress(
                provider.getMemberCode());
        if (addr == null) {
            return emptySet();
        } else {
            return singleton(addr);
        }
    }

    @Override
    public Set<SecurityCategoryId> getProvidedCategories(
            X509Certificate authCert) {
        return currentTestCase().getProvidedCategories();
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        try {
            return Arrays.asList(TestCertUtil.getOcspSigner().cert);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        return new X509Certificate[] {TestCertUtil.getCaCert()};
    }

    private MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier,
            X509Certificate org) throws Exception {
        return TestCertUtil.getCaCert();
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        return CertChain.create(instanceIdentifier, subject, null);
    }

    @Override
    public List<X509Certificate> getTspCertificates()
            throws CertificateException {
        return Arrays.asList(TestCertUtil.getTspCert());
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate cert, ClientId memberId)
            throws Exception {
        return true;
    }

    @Override
    public SignCertificateProfileInfo getSignCertificateProfileInfo(
            SignCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception {
        return new EjbcaSignCertificateProfileInfo(parameters) {
            @Override
            public ClientId getSubjectIdentifier(X509Certificate certificate) {
                // Currently the test certificate contains invalid member class
                // so we just fix the member class here instead of regenerating
                // new certificate.
                ClientId id = super.getSubjectIdentifier(certificate);
                return ClientId.create(
                    id.getXRoadInstance(),
                    "BUSINESS",
                    id.getMemberCode()
                );
            }
        };
    }

    @Override
    public AuthCertificateProfileInfo getAuthCertificateProfileInfo(
            AuthCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception {
        return null;
    }
}

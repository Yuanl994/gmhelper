package org.zz.gmhelper.cert;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.zz.gmhelper.Sm2Util;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;

public class Sm2X509CertMaker {
    public static final String SIGN_ALGO_SM3WITHSM2 = "SM3withSM2";

    private long certExpire;
    private X500Name issuerDN;
    private CertSNAllocator snAllocator;
    private KeyPair issuerKeyPair;
    private JcaContentSignerBuilder contentSignerBuilder;

    /**
     * @param issuerKeyPair 证书颁发者的密钥对
     * @param certExpire    证书有效时间，单位毫秒
     * @param issuer        证书颁发者信息
     * @param snAllocator   维护/分配证书序列号的实例，证书序列号应该递增且不重复
     */
    public Sm2X509CertMaker(KeyPair issuerKeyPair, long certExpire, X500Name issuer, CertSNAllocator snAllocator) {
        this.issuerKeyPair = issuerKeyPair;
        this.certExpire = certExpire;
        this.issuerDN = issuer;
        this.snAllocator = snAllocator;

        if (issuerKeyPair.getPublic().getAlgorithm().equals("EC")) {
            this.contentSignerBuilder = new JcaContentSignerBuilder(SIGN_ALGO_SM3WITHSM2);
            this.contentSignerBuilder.setProvider(BouncyCastleProvider.PROVIDER_NAME);
        } else {
            throw new RuntimeException("Unsupported PublicKey Algorithm:" + issuerKeyPair.getPublic().getAlgorithm());
        }
    }

    /**
     * @param isCA     是否是颁发给中级CA的证书
     * @param keyUsage 证书用途
     * @param csr      CSR
     * @return
     * @throws Exception
     */
    public X509Certificate makeCertificate(boolean isCA, KeyUsage keyUsage, byte[] csr)
        throws Exception {
        PKCS10CertificationRequest request = new PKCS10CertificationRequest(csr);
        PublicKey subPub = Sm2Util.convertPublicKey(request.getSubjectPublicKeyInfo());
        PrivateKey issPriv = issuerKeyPair.getPrivate();
        PublicKey issPub = issuerKeyPair.getPublic();

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        X509v3CertificateBuilder v3CertGen = new JcaX509v3CertificateBuilder(issuerDN, snAllocator.incrementAndGet(),
            new Date(System.currentTimeMillis()), new Date(System.currentTimeMillis() + certExpire),
            request.getSubject(), subPub);
        v3CertGen.addExtension(Extension.subjectKeyIdentifier, false,
            extUtils.createSubjectKeyIdentifier(SubjectPublicKeyInfo.getInstance(subPub.getEncoded())));
        v3CertGen.addExtension(Extension.authorityKeyIdentifier, false,
            extUtils.createAuthorityKeyIdentifier(SubjectPublicKeyInfo.getInstance(issPub.getEncoded())));
        v3CertGen.addExtension(Extension.basicConstraints, false, new BasicConstraints(isCA));
        v3CertGen.addExtension(Extension.keyUsage, false, keyUsage);

        JcaContentSignerBuilder contentSignerBuilder = makeContentSignerBuilder(issPub);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(v3CertGen.build(contentSignerBuilder.build(issPriv)));
        cert.checkValidity(new Date());
        cert.verify(issPub);

        return cert;
    }

    private JcaContentSignerBuilder makeContentSignerBuilder(PublicKey issPub) throws Exception {
        if (issPub.getAlgorithm().equals("EC")) {
            JcaContentSignerBuilder contentSignerBuilder = new JcaContentSignerBuilder(SIGN_ALGO_SM3WITHSM2);
            contentSignerBuilder.setProvider(BouncyCastleProvider.PROVIDER_NAME);
            return contentSignerBuilder;
        }
        throw new Exception("Unsupported PublicKey Algorithm:" + issPub.getAlgorithm());
    }
}

package ee.ria.xroad.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

/**
 * Signer API message.
 */
@Value
public class GenerateCertRequestResponse implements Serializable {

    private final String certReqId;

    private final byte[] certRequest;

    private GenerateCertRequest.RequestFormat format;
}

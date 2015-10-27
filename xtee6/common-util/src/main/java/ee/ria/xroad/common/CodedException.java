package ee.ria.xroad.common;

import java.io.Serializable;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Exception thrown by proxy business logic. Contains SOAP fault information
 * (code, message, actor and detail).
 */
public class CodedException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 4225113353511950429L;

    @Getter
    protected String faultCode;

    @Getter
    protected String faultActor = "";

    @Getter
    @Setter
    protected String faultDetail = "";

    @Getter
    protected String faultString = "";

    @Getter
    protected String[] arguments;

    @Getter
    protected String translationCode;

    /**
     * Creates new exception using the fault code.
     * @param faultCode the fault code
     */
    public CodedException(String faultCode) {
        this.faultCode = faultCode;
        faultDetail = String.valueOf(UUID.randomUUID());
    }

    /**
     * Creates new exception with fault code and fault message.
     * @param faultCode the fault code
     * @param faultMessage the message
     */
    public CodedException(String faultCode, String faultMessage) {
        super(faultMessage);

        this.faultCode = faultCode;
        faultDetail = String.valueOf(UUID.randomUUID());
        faultString = faultMessage;
    }

    /**
     * Creates new exception with fault code and fault message.
     * The message is constructed using String.format(). from parameters
     * format and args.
     * @param faultCode the fault code
     * @param format the string format
     * @param args the arguments
     */
    public CodedException(String faultCode, String format, Object... args) {
        this(faultCode, String.format(format, args));

        setArguments(args);
    }

    /**
     * Creates exception from fault code and cause.
     * @param faultCode the fault code
     * @param cause the cause
     */
    public CodedException(String faultCode, Throwable cause) {
        super(cause);

        this.faultCode = faultCode;
        this.faultDetail = String.valueOf(UUID.randomUUID());
        this.faultString = cause.getMessage();
    }

    /**
     * Creates new exception with full SOAP fault details.
     * @param faultCode the fault code
     * @param faultString the fault string (e.g. the message)
     * @param faultActor the fault actor
     * @param faultDetail the details
     * @param faultXml the XML document representing the fault
     * @return new proxy exception
     */
    public static CodedException fromFault(String faultCode, String faultString,
            String faultActor, String faultDetail, String faultXml) {
        Fault ret = new Fault(faultCode, faultString);

        ret.faultActor = faultActor;
        ret.faultDetail = faultDetail;

        ret.faultXml = faultXml;

        return ret;
    }

    /**
     * Creates new exception with translation code for i18n.
     * @param faultCode the fault code
     * @param trCode the translation code
     * @param faultMessage the message
     * @return CodedException
     */
    public static CodedException tr(String faultCode, String trCode,
            String faultMessage) {
        CodedException ret = new CodedException(faultCode, faultMessage);

        ret.translationCode = trCode;

        return ret;
    }

    /**
     * Creates new exception with translation code for i18n and arguments.
     * @param faultCode the fault code
     * @param trCode the translation code
     * @param faultMessage the message
     * @param args optional arguments
     * @return CodedException
     */
    public static CodedException tr(String faultCode, String trCode,
            String faultMessage, Object... args) {
        CodedException ret = new CodedException(faultCode, faultMessage, args);

        ret.translationCode = trCode;

        return ret;
    }

    @Override
    public String getMessage() {
        return toString();
    }

    /**
     * Returns the string representation of the exception in form of
     * [code]: [detail].
     * @return String
     */
    @Override
    public String toString() {
        return faultCode + ": " + faultString;
    }

    /**
     * Returns the current exception with prefix appended in front of
     * the fault code.
     * @param prefixes optional prefixes
     * @return CodedException
     */
    public CodedException withPrefix(String... prefixes) {
        String prefix = StringUtils.join(prefixes, ".");

        if (!faultCode.startsWith(prefix)) {
            faultCode = prefix + "." + faultCode;
        }

        return this;
    }

    /**
     * Converts provided arguments to string array.
     */
    private void setArguments(Object... args) {
        arguments = new String[args.length];

        for (int i = 0; i < args.length; i++) {
            arguments[i] = args[i] != null ? args[i].toString() : "";
        }
    }

    /**
     * Encapsulates error message read from SOAP fault.
     * This allows processing faults separately in ClientProxy.
     */
    @SuppressWarnings("serial") // does not need to have serial
    public static class Fault extends CodedException implements Serializable {

        @Getter
        private String faultXml;

        /**
         * Creates new fault.
         * @param faultCode the code
         * @param faultString the details
         */
        public Fault(String faultCode, String faultString) {
            super(faultCode, faultString);
        }
    }
}

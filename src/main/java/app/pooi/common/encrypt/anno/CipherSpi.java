package app.pooi.common.encrypt.anno;

import java.util.List;
import java.util.Map;

public interface CipherSpi {

    Map<String, String> decrypt(List<String> values);
}

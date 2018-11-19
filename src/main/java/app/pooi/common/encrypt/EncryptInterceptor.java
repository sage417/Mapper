package app.pooi.common.encrypt;


import app.pooi.common.encrypt.anno.CipherSpi;
import app.pooi.common.encrypt.anno.Encrypted;
import lombok.Getter;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Field;
import java.sql.Statement;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Intercepts({
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class}),
})
public class EncryptInterceptor implements Interceptor {

    private static final Logger logger = Logger.getLogger(EncryptInterceptor.class.getName());

    private CipherSpi cipherSpi;

    public EncryptInterceptor(CipherSpi cipherSpi) {
        this.cipherSpi = cipherSpi;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        final Object proceed = invocation.proceed();

        if (proceed == null) {
            return proceed;
        }

        List<?> results = (List<?>) proceed;

        if (results.isEmpty()) {
            return proceed;
        }

        final Object first = results.iterator().next();

        final Class<?> modelClazz = first.getClass();

        final List<String> fieldsNeedDecrypt = Arrays.stream(modelClazz.getDeclaredFields())
                .filter(f -> f.getAnnotation(Encrypted.class) != null)
                .filter(f -> {
                    boolean isString = f.getType() == String.class;
                    if (!isString) {
                        logger.warning(f.getName() + "is not String, actual type is " + f.getType().getSimpleName() + " ignored");
                    }
                    return isString;
                })
                .map(Field::getName)
                .collect(Collectors.toList());

        final List<List<String>> partition = partition(fieldsNeedDecrypt, 20);

        for (Object r : results) {
            final MetaObject metaObject = SystemMetaObject.forObject(r);

            for (List<String> fields : partition) {
                final Map<String, String> fieldValueMap = fields.stream().collect(Collectors.toMap(Function.identity(), f -> (String) metaObject.getValue(f)));
                final ArrayList<String> values = new ArrayList<>(fieldValueMap.values());
                Map<String, String> decryptValues = cipherSpi.decrypt(values);

                fieldValueMap.entrySet()
                        .stream()
                        .map(e -> Tuple2.of(e.getKey(), decryptValues.getOrDefault(e.getValue(), "")))
                        .forEach(e -> metaObject.setValue(e.getT1(), e.getT2()));
            }
        }

        return results;
    }

    private <T> List<List<T>> partition(List<T> list, int batchCount) {
        if (!(batchCount > 0)) {
            throw new IllegalArgumentException("batch count must greater than zero");
        }

        List<List<T>> partitionList = new ArrayList<>(list.size() / (batchCount + 1));

        for (int i = 0; i < list.size(); i += batchCount) {
            partitionList.add(list.stream().skip(i).limit(batchCount).collect(Collectors.toList()));

        }
        return partitionList;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}

@Getter
class Tuple2<T1, T2> {

    private final T1 t1;

    private final T2 t2;

    Tuple2(T1 t1, T2 t2) {
        this.t1 = t1;
        this.t2 = t2;
    }

    static <T1, T2> Tuple2<T1, T2> of(T1 t1, T2 t2) {
        return new Tuple2<>(t1, t2);
    }
}

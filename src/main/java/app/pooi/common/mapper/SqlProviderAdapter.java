package app.pooi.common.mapper;

import lombok.Getter;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import javax.persistence.Id;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SqlProviderAdapter {

    static final String CONDITION = "condition";
    static final String PARAMETERS = "parameters";

    private static final Map<Class<?>, Class<?>> mapperDbModels = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> modeTableNames = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<String>> modelPrimaryKeys = new ConcurrentHashMap<>();

    public String selectById(ProviderContext context) {

        final Class<?> dbModelClazz = getDBModelClazz(context.getMapperType());
        final List<String> primaryKeys = primaryKey(dbModelClazz);

        final String[] ids = primaryKeys.stream()
                .map(pk -> String.format("`%s` = #{%s}", pk, pk))
                .toArray(String[]::new);

        return new SQL() {{
            SELECT("*");
            FROM(tableName(dbModelClazz));
            WHERE(ids);
        }}.toString();
    }

    public String insertSelective(ProviderContext context, Object record) {

        Objects.requireNonNull(record, "record is required nonnull");

        final MetaObject msObject = SystemMetaObject.forObject(record);

        final List<String> properties = Arrays.stream(msObject.getGetterNames())
                .map(name -> Tuple2.of(name, msObject.getValue(name)))
                .filter(tuple2 -> tuple2.getT2() != null)
                .map(Tuple2::getT1)
                .collect(Collectors.toList());

        final String[] columns = properties.toArray(new String[0]);

        final String[] values = properties.stream()
                .map(name -> String.format("#{%s}", name))
                .toArray(String[]::new);

        return new SQL() {{
            INSERT_INTO(tableName(getDBModelClazz(context.getMapperType())));
            INTO_COLUMNS(columns);
            INTO_VALUES(values);
        }}.toString();
    }

    public String updateSelective(ProviderContext context, Object record) {
        Objects.requireNonNull(record, "parameters is required nonnull");

        final MetaObject msObject = SystemMetaObject.forObject(record);

        final String[] sets = Arrays.stream(msObject.getGetterNames())
                .map(name -> Tuple2.of(name, msObject.getValue(name)))
                .filter(tuple2 -> tuple2.getT2() != null)
                .map(tuple2 -> String.format("`%s` = #{%s}", tuple2.getT1(), tuple2.getT1()))
                .toArray(String[]::new);

        final Class<?> dbModelClazz = getDBModelClazz(context.getMapperType());
        final List<String> primaryKeys = primaryKey(dbModelClazz);

        final String[] ids = primaryKeys.stream()
                .map(pk -> String.format("`%s` = #{%s}", pk, pk))
                .toArray(String[]::new);

        return new SQL() {{
            UPDATE(tableName(dbModelClazz));
            SET(sets);
            WHERE(ids);
        }}.toString();
    }

    public String updateSelectiveConditional(ProviderContext context, Map<String, Object> params) {

        final Object record = params.get(PARAMETERS);
        Objects.requireNonNull(record, "parameters is required nonnull");
        final Object condition = params.get(CONDITION);
        Objects.requireNonNull(condition, "condition is required nonnull");

        final MetaObject msObject = SystemMetaObject.forObject(record);
        final MetaObject exceptObject = SystemMetaObject.forObject(condition);

        final Class<?> dbModelClazz = getDBModelClazz(context.getMapperType());
        final List<String> primaryKeys = primaryKey(dbModelClazz);

        final String[] sets = Arrays.stream(msObject.getGetterNames())
                .map(name -> Tuple2.of(name, msObject.getValue(name)))
                .filter(tuple2 -> tuple2.getT2() != null)
                .map(tuple2 -> String.format("`%s` = #{%s.%s}", tuple2.getT1(), PARAMETERS, tuple2.getT1()))
                .toArray(String[]::new);

        final String[] wheres = Stream.concat(primaryKeys.stream()
                        .map(pk -> String.format("`%s` = #{%s.%s}", pk, CONDITION, pk)),
                Arrays.stream(exceptObject.getSetterNames())
                        .map(name -> Tuple2.of(name, exceptObject.getValue(name)))
                        .filter(tuple2 -> tuple2.getT2() != null)
                        .map(tuple2 -> String.format("`%s` = #{%s.%s}", tuple2.getT1(), CONDITION, tuple2.getT1()))
        ).toArray(String[]::new);

        return new SQL() {{
            UPDATE(tableName(dbModelClazz));
            SET(sets);
            WHERE(wheres);
        }}.toString();
    }


    private List<String> primaryKey(Class<?> dbModelClazz) {

        final List<String> primaryKeys = modelPrimaryKeys.get(dbModelClazz);

        if (primaryKeys != null) {
            return primaryKeys;
        }

        Field[] fields = dbModelClazz.getDeclaredFields();
//        Arrays.stream(fields).forEach(f -> f.setAccessible(true));

        final List<String> keys = Arrays.stream(fields)
                .filter(f -> f.getAnnotation(Id.class) != null)
                .map(Field::getName)
                .collect(Collectors.toList());

        if (keys.isEmpty()) {
            throw new IllegalStateException("could not found @Id annotation at class " + dbModelClazz.getName());
        }

        modelPrimaryKeys.putIfAbsent(dbModelClazz, keys);
        return modelPrimaryKeys.get(dbModelClazz);
    }

    /**
     * 获取 数据库model上@Table name属性
     *
     * @param dbModelClazz
     * @return table name
     */
    private String tableName(Class<?> dbModelClazz) {
        final String name = modeTableNames.get(dbModelClazz);
        if (name != null) {
            return name;
        }

        final Table table = dbModelClazz.getAnnotation(Table.class);
        Objects.requireNonNull(table, "could not found @Table at class:" + dbModelClazz.getName());
        Objects.requireNonNull(table.name(), "@Table name() is null!");

        modeTableNames.putIfAbsent(dbModelClazz, table.name());
        return modeTableNames.get(dbModelClazz);
    }

    /**
     * 获取extends BaseMapper Mapper的范型参数
     *
     * @param mapperClazz extends BaseMapper的Mapper class
     * @return 范型参数
     */
    private Class<?> getDBModelClazz(Class<?> mapperClazz) {
        Class<?> dbModel = mapperDbModels.get(mapperClazz);
        if (dbModel != null) {
            return dbModel;
        }

        dbModel = Arrays.stream(mapperClazz.getGenericInterfaces())
                .filter(t -> t instanceof ParameterizedType)
                .map(t -> (ParameterizedType) t)
                .filter(t -> t.getRawType() == BaseMapper.class)
                .map(p -> p.getActualTypeArguments()[0])
                .map(p -> (Class<?>) p)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("获取" + mapperClazz.getName() + "范型失败"));

        mapperDbModels.putIfAbsent(mapperClazz, dbModel);
        return mapperDbModels.get(mapperClazz);
    }
}

@Getter
class Tuple2<T1, T2> {

    private final T1 t1;

    private final T2 t2;

    private Tuple2(T1 t1, T2 t2) {
        this.t1 = t1;
        this.t2 = t2;
    }

    static <T1, T2> Tuple2<T1, T2> of(T1 t1, T2 t2) {
        return new Tuple2<>(t1, t2);
    }
}

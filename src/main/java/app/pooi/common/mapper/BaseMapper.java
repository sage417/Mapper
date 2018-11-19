package app.pooi.common.mapper;

import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

public interface BaseMapper<T> {

    /**
     * select by primary key
     *
     * @param primaryKey entity id
     * @return entity
     */
    @SelectProvider(type = SqlProviderAdapter.class, method = "selectById")
    T selectByPrimaryKey(long primaryKey);


    /**
     * insert record
     *
     * @param record
     * @return affect row count
     */
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertSelective")
    int insertSelective(T record);


    /**
     * update record
     *
     * @param record
     * @return affect row count
     */
    @UpdateProvider(type = SqlProviderAdapter.class, method = "updateSelective")
    int updateSelectiveById(T record);


    /**
     * update record
     *
     * @param record
     * @return affect row count
     */
    @UpdateProvider(type = SqlProviderAdapter.class, method = "updateSelectiveConditional")
    int updateSelectiveConditionalById(@Param(SqlProviderAdapter.CONDITION) T condition, @Param(SqlProviderAdapter.PARAMETERS) T record);
}



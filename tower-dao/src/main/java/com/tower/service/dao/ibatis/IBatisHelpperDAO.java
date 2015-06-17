package com.tower.service.dao.ibatis;

import javax.sql.DataSource;

import com.tower.service.dao.IHelpperDAO;

/**
 * ibatis 操作接口
 * 
 * @author alexzhu
 *
 * @param <T>
 */
public interface IBatisHelpperDAO<T> extends IHelpperDAO<T> {

  public Class<? extends IHelpperMapper<T>> getMapperClass();

  public DataSource getMasterDataSource();

  public DataSource getMapQueryDataSource();

  public void validate(T model);

}

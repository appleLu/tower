package com.tower.service.dao;

import java.util.List;

public interface IHelpper<T> {

  public String getTKjtTabName();
  
  public void setTKjtTabName(String tKjtTabName);
  
  public void setOrderByClause(String orderByClause);

  public String getOrderByClause();

  public void setDistinct(boolean distinct);

  public boolean isDistinct();

  public List<T> getOredCriteria();

  public void or(T criteria);

  public T or();

  public T createCriteria();

  public T createCriteriaInternal();

  public void clear();

}
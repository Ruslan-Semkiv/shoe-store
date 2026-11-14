package com.shoesshop.com.repository.common;

import com.shoesshop.com.exception.ItemNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Shared base for the repositories; not a repository itself. */
@NoRepositoryBean
public interface ValidatorJpaRepository <T,ID> extends JpaRepository<T,ID> {

  default T getOrElseThrow(ID id , String exceptionMessage) {
    return findById(id).orElseThrow(() -> new ItemNotFoundException(exceptionMessage + id));
  }
}

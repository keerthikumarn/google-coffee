package com.googlecoffee.port;

import com.googlecoffee.model.MenuItem;
 
import java.util.List;
 
public interface MenuStore {
    List<MenuItem> findAll();
 
    void saveAll(List<MenuItem> items);
}

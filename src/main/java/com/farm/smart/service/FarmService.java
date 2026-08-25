package com.farm.smart.service;

import com.farm.smart.model.entity.Farm;
import com.farm.smart.model.entity.Field;

import java.util.List;

/**
 * 农场管理服务接口
 *
 * @author Smart Farm Team
 */
public interface FarmService {

    /**
     * 获取所有农场列表
     */
    List<Farm> listFarms();

    /**
     * 根据ID获取农场
     */
    Farm getFarmById(Long id);

    /**
     * 创建农场
     */
    Farm createFarm(Farm farm);

    /**
     * 更新农场信息
     */
    Farm updateFarm(Farm farm);

    /**
     * 删除农场 (软删除)
     */
    boolean deleteFarm(Long id);

    /**
     * 获取农场下所有地块
     */
    List<Field> listFields(Long farmId);

    /**
     * 创建地块
     */
    Field createField(Field field);

    /**
     * 更新地块信息
     */
    Field updateField(Field field);

    /**
     * 删除地块
     */
    boolean deleteField(Long id);
}

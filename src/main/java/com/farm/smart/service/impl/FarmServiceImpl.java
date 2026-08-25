package com.farm.smart.service.impl;

import com.farm.smart.model.entity.Farm;
import com.farm.smart.model.entity.Field;
import com.farm.smart.repository.FarmMapper;
import com.farm.smart.repository.FieldMapper;
import com.farm.smart.service.FarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 农场管理服务实现
 *
 * @author Smart Farm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmServiceImpl implements FarmService {

    private final FarmMapper farmMapper;
    private final FieldMapper fieldMapper;

    @Override
    public List<Farm> listFarms() {
        log.info("查询所有农场列表");
        return farmMapper.selectList(null);
    }

    @Override
    public Farm getFarmById(Long id) {
        Farm farm = farmMapper.selectById(id);
        if (farm == null) {
            throw new IllegalArgumentException("农场不存在: id=" + id);
        }
        return farm;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Farm createFarm(Farm farm) {
        farm.setStatus(1);
        farm.setCreateTime(LocalDateTime.now());
        farm.setUpdateTime(LocalDateTime.now());
        farmMapper.insert(farm);
        log.info("创建农场成功: id={}, name={}", farm.getId(), farm.getName());
        return farm;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Farm updateFarm(Farm farm) {
        Farm existing = getFarmById(farm.getId());
        farm.setUpdateTime(LocalDateTime.now());
        farm.setCreateTime(existing.getCreateTime());
        farmMapper.updateById(farm);
        log.info("更新农场成功: id={}", farm.getId());
        return farm;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFarm(Long id) {
        // 校验农场存在
        getFarmById(id);
        int result = farmMapper.deleteById(id);
        log.info("删除农场: id={}, result={}", id, result);
        return result > 0;
    }

    // ==================== 地块管理 ====================

    @Override
    public List<Field> listFields(Long farmId) {
        log.info("查询农场地块列表: farmId={}", farmId);
        return fieldMapper.selectByFarmId(farmId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Field createField(Field field) {
        // 校验农场存在
        getFarmById(field.getFarmId());
        field.setStatus(1);
        field.setCreateTime(LocalDateTime.now());
        field.setUpdateTime(LocalDateTime.now());
        fieldMapper.insert(field);
        log.info("创建地块成功: id={}, name={}", field.getId(), field.getName());
        return field;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Field updateField(Field field) {
        field.setUpdateTime(LocalDateTime.now());
        fieldMapper.updateById(field);
        log.info("更新地块成功: id={}", field.getId());
        return field;
    }

    @Override
    public boolean deleteField(Long id) {
        int result = fieldMapper.deleteById(id);
        log.info("删除地块: id={}, result={}", id, result);
        return result > 0;
    }
}

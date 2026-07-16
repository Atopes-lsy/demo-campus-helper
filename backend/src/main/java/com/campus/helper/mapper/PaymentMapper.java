package com.campus.helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.helper.entity.Payment;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

public interface PaymentMapper extends BaseMapper<Payment> {

    @Select("SELECT COUNT(*) AS cnt, SUM(amount) AS total FROM payment WHERE pay_status IN (2,3) AND created_at >= CURDATE()")
    Map<String, Object> selectOverview();
}

package com.campus.helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.helper.entity.Runner;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface RunnerMapper extends BaseMapper<Runner> {

    @Select("SELECT *, (completed_orders * 0.4 + rating * 0.6) AS score FROM runner ORDER BY score DESC LIMIT #{limit}")
    List<Map<String, Object>> selectTopRunners(@Param("limit") int limit);
}

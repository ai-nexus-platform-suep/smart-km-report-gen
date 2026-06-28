package com.myenglish.qachat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myenglish.qachat.entity.Message;
import com.myenglish.qachat.mapper.dto.DailyCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("""
            SELECT COUNT(*)
            FROM qa_message
            WHERE status = #{status}
              AND role = #{role}
              AND intent_type = #{intentType}
              AND generate_status = #{generateStatus}
            """)
    long countKnowledgeQa(@Param("status") int status,
                          @Param("role") String role,
                          @Param("intentType") String intentType,
                          @Param("generateStatus") int generateStatus);

    @Select("""
            SELECT DATE(created_at) AS stat_date, COUNT(*) AS count
            FROM qa_message
            WHERE status = #{status}
              AND role = #{role}
              AND intent_type = #{intentType}
              AND generate_status = #{generateStatus}
              AND created_at >= #{startAt}
            GROUP BY DATE(created_at)
            ORDER BY stat_date
            """)
    List<DailyCountRow> selectDailyKnowledgeQaCount(@Param("status") int status,
                                                    @Param("role") String role,
                                                    @Param("intentType") String intentType,
                                                    @Param("generateStatus") int generateStatus,
                                                    @Param("startAt") LocalDateTime startAt);
}

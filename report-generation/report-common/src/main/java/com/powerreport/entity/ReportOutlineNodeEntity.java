package com.powerreport.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("report_outline_nodes")
public class ReportOutlineNodeEntity {

    @TableId
    private String id;

    private String reportId;
    private String parentId;
    private Integer level;
    private Integer sortOrder;
    private String number;
    private String title;
    private String promptHint;

    @TableField(exist = false)
    private String tableJson;
    private LocalDateTime createdAt;
}

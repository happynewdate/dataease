1、数据源对应的维度字段中拼接上需要排序的字段

```mysql
CONCAT(数字/数值字段, ' #sort# ', 维度字段)
```

eg: 通过yee_sys_org表中的id进行排序

```mysql
WITH RECURSIVE org_tree AS (
    SELECT 
        o.id,
        o.parent_org_id,
        o.org_name,
        o.org_code,
        o.org_level_code,
        o.org_type,
        o.biz_type,
        o.node_type
    FROM yee_sys_org o
    WHERE o.org_code = '7611282360'

    UNION ALL

    SELECT 
        c.id,
        c.parent_org_id,
        c.org_name,
        c.org_code,
        c.org_level_code,
        c.org_type,
        c.biz_type,
        c.node_type
    FROM yee_sys_org c
    INNER JOIN org_tree t ON c.parent_org_id = t.id
    WHERE c.org_type IN (
        1,2,3,
        613643492274606080,
        613643493176381440,
        613643495806210048
    )
),
main_data AS (
    SELECT
        ot.id AS org_id,  -- 新增：获取yee_sys_org对应的id
        x.xfmc,
        ROUND(x.zyywsrze,2) AS zyywsrze,
        ROUND(x.cbfyze,2)  AS cbfyze,
        (x.cbfyze / x.zyywsrze) AS ratio
    FROM xjydxypt_xfszc_tzhjcsj x
    -- 新增关联：通过oa_dept_code和org_code关联获取id
    INNER JOIN org_tree ot ON x.oa_dept_code = ot.org_code
    WHERE x.month = '2025-11'
)

SELECT
    ROW_NUMBER() OVER (ORDER BY ratio DESC) AS sort_no,
    org_id,  -- 最终结果中显示org_id
    concat(org_id,'#sort#',xfmc) xfmc,
    zyywsrze,
    cbfyze,
    ratio
FROM main_data ORDER BY org_id DESC limit 10
```

eg:如果想根据ratio字段进行排序

```mysql
WITH RECURSIVE org_tree AS (
    SELECT 
        o.id,
        o.parent_org_id,
        o.org_name,
        o.org_code,
        o.org_level_code,
        o.org_type,
        o.biz_type,
        o.node_type
    FROM yee_sys_org o
    WHERE o.org_code = '7611282360'

    UNION ALL

    SELECT 
        c.id,
        c.parent_org_id,
        c.org_name,
        c.org_code,
        c.org_level_code,
        c.org_type,
        c.biz_type,
        c.node_type
    FROM yee_sys_org c
    INNER JOIN org_tree t ON c.parent_org_id = t.id
    WHERE c.org_type IN (
        1,2,3,
        613643492274606080,
        613643493176381440,
        613643495806210048
    )
),
main_data AS (
    SELECT
        ot.id AS org_id,  -- 新增：获取yee_sys_org对应的id
        x.xfmc,
        ROUND(x.zyywsrze,2) AS zyywsrze,
        ROUND(x.cbfyze,2)  AS cbfyze,
        (x.cbfyze / x.zyywsrze) AS ratio
    FROM xjydxypt_xfszc_tzhjcsj x
    -- 新增关联：通过oa_dept_code和org_code关联获取id
    INNER JOIN org_tree ot ON x.oa_dept_code = ot.org_code
    WHERE x.month = '2025-11'
)

SELECT
    ROW_NUMBER() OVER (ORDER BY ratio DESC) AS sort_no,
    org_id,  -- 最终结果中显示org_id
    concat(ratio,'#sort#',xfmc) xfmc,
    zyywsrze,
    cbfyze,
    ratio
FROM main_data ORDER BY ratio DESC limit 10
```

2、在数据源对应图表的维度中设置升序还是降序

<img src="https://sanhua.jcsk100.com/admin/sys/file/download/773a5990c7bff3ebf9a813be237936e2" alt="image-20251231104641891" style="zoom:50%;" />

3、更新图表查看对应的效果

根据yee_sys_org.id 降序的结果

<img src="https://sanhua.jcsk100.com/admin/sys/file/download/b139f26d6b542e53959927675c08fd40" alt="image-20251231105124887" style="zoom:50%;" />

根据ratio 降序的结果

<img src="https://sanhua.jcsk100.com/admin/sys/file/download/e2cf6741259f9f60eb8217e12c54f565" alt="image-20251231105854717" style="zoom:50%;" />


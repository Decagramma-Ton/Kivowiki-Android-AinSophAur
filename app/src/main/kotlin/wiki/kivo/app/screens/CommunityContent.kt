package wiki.kivo.app.screens

/**
 * 2026-09-07 从站点公开页面核对。网站将名单写在前端资源中，尚无独立公开 API。 保留分组含义、毕业说明与旧站赞助金额；这些记录不代表现役成员或当前募捐。 更新名单时同步核对
 * /contributor，并更新页面核对日期。
 */
internal data class Contributor(
    val name: String,
    val description: String,
    val avatar: String? = null,
)

internal data class ContributorGroup(
    val title: String,
    val description: String,
    val people: List<Contributor>,
)

private fun person(name: String, description: String, image: String) =
    Contributor(name, description, "https://kivo.wiki/images/contributor/$image.webp")

internal val contributorGroups =
    listOf(
        ContributorGroup(
            "站务",
            "头上长感叹号的家伙们，时不时让你回一趟沙之家（不",
            listOf(
                person("NijiNeko", "技术支持与维护，古书馆的大建筑师", "CB_01"),
                person("朝禊", "页面策划、组织管理与站点运营，赛博工头", "CB_02"),
            ),
        ),
        ContributorGroup(
            "美术",
            "大书库的艺术家们，美的创造者",
            listOf(
                person("崩崩bun", "站娘的设计与原画", "CB_03"),
                person("画纱", "404、施工、维护页面的站娘 Q 版小人的绘制", "CB_04"),
                person("GGG", "标签页图标、加载动画的站娘像素小人的绘制", "CB_28"),
            ),
        ),
        ContributorGroup(
            "翻新贡献者",
            "奇迹的新生离不开这些可爱的人们的辛勤付出，风信子的骑士们。\n此名单记录 2023 年为新站上线做出贡献的人们，并非现役组员。",
            listOf(
                person("白羽あき", "夏影亡(な)去即是秋(あき)", "CB_05"),
                person("奇幻滅", "不是灭火器", "CB_06"),
                person("白羽かなた", "Hello to Halo", "CB_07"),
                person("huy", "Straylight", "CB_08"),
                person("阿溱", "单推朝禊Asogi", "CB_09"),
                person("Momoi", "接游戏代打", "CB_10"),
                person("星泠鑫", "启明星", "CB_11"),
                person("Riyoriyo", "May the force be with you", "CB_12"),
                person("カズシロ", "古法烤肉学家", "CB_13"),
                person("nakayoshi", "Tres,duo,unus.", "CB_14"),
                person("四费水管工", "六费大猩猩", "CB_15"),
                person("緒山真寄", "从古代服务器中逃逸而出", "CB_16"),
                person("界徐盛", "犯大吴疆土者,盛必击而破之", "CB_17"),
                person("IRST", "不是红外搜索与跟踪更不是英特尔快速存储技术", "CB_18"),
                person("星序", "遵循繁星的秩序", "CB_19"),
                person("蓝天Aoyisula", "黑蜥蜴星人", "CB_20"),
            ),
        ),
        ContributorGroup(
            "荣誉室",
            "编辑组的毕业生们，感谢他们曾经为古书馆做出的贡献！\n此名单记录主动告知离组、在组内和平毕业的部分成员。他们不再负责站内施工。",
            listOf(
                person(
                    "咲Rany",
                    "写文预备中……我们下次再见！\n2024年2月～2025年10月\n曾负责：每周日常维护、生放信息填写、图集资料施工",
                    "CB_29",
                ),
                person(
                    "DaMask66",
                    "感谢你建立的频道，辛苦了，祝你的未来一帆风顺，祝古书馆越办越好。\n2024年9月～2026年1月\n曾负责：寿司战队及寿司机器人详细资料、国际服「小雪骇入事件」相关资料、每周日常维护、图集资料施工、角色资料补充、其他组内大规模批量施工",
                    "CB_30",
                ),
                person(
                    "SaltySakana",
                    "维护古书馆的各位辛苦了，祝古书馆顺利，未来再见。\n2025年4月～2026年5月\n曾负责：「便利屋68」「温泉开发部」「阴阳部」「美食研究会」社团资料、供给部关系词条有关朱莉的内容",
                    "CB_31",
                ),
            ),
        ),
        ContributorGroup(
            "旧站赞助者",
            "过去古书馆的养站贵人们，即使如今重获新生也不应将其忘却。以下为旧站历史记录。",
            listOf(
                person("最爱朝朝", "￥320 · 最爱", "sponsor/CB_21"),
                person("结月缘本人", "￥80", "sponsor/CB_22"),
                person("星泠鑫", "￥30 · 祝越办越好", "CB_11"),
                person("you%", "￥99 · 暖成咖啡，安静的拿给你，不要说话", "sponsor/CB_23"),
                person("蓝天Aoyisula", "￥90 · 希望大赐福古书馆还有大家都能坚持下去吧大概 加油加油!", "CB_20"),
                person("酷酷的野生qa君", "￥169.2 · 感谢你们对 BA 社区的贡献", "sponsor/CB_24"),
                person("隆德维格", "￥84.6 · B站ID：机智炯炯诺西罗", "sponsor/CB_25"),
                person("錦上紗", "￥30 · 建设社区氛围与资源整合都不是件容易的事 这是一点小心意～", "sponsor/CB_26"),
                Contributor("星河辉夜", "￥100 · 感谢古书馆为我的显卡改造提供的素材"),
            ),
        ),
    )

internal val contactMarkdown =
    """
    ## 组外协助
    ### 反馈收集表
    可以反馈问题或直接供稿！选择公开的贡献者会标注在对应页面及[报刊亭文章](https://kivo.wiki/article/32)中。

    [填写反馈收集表](https://www.wjx.cn/vm/O3KsQNO.aspx)

    ### 古书馆 QQ 频道
    在对应板块发帖反馈，或与我们直接联系。

    [打开 QQ 频道](https://pd.qq.com/g/Nekuso0721)

    ### B 站值班室
    在置顶动态下留言或直接私信；站内更新动态也会发布在这里。

    [前往古书馆值班室](https://space.bilibili.com/3494372842670276/dynamic)

    ### BUG 反馈
    技术问题可通过邮箱 nijinekoyo@outlook.com 向网站技术反馈。

    ## 加入我们
    成为编辑组的一员，直接对页面进行编辑和改进。与小遁一起建设大家的古书馆，书写属于我们的奇迹！

    > KIVOWIKI は、全ての先生のためにあります。

    招募方向：
    - 日常填表维护
    - 剧情资料撰写
    - 梗、meme 等词条资料整理
    - 日语翻译

    [填写进组申请表](https://www.wjx.cn/vm/PpItXAo.aspx)

    ### 关于古书馆及编辑组
    - 早期新站刚建立时的多组结构已废弃，目前内容建设统一为「编辑组」。
    - 本站服务器由官方直接支持，无资金压力。
    - 编辑组更接近社团活动，成员由热心玩家组成，用爱发电，负责站内建设和各项事务协助。
    - 施工建设没有强迫性，也没有既定收益和报酬。可以随时放弃或离开，自己的事情和生活最优先，请不要因协助而耽误生活。
    - 可以同时协助其他 BA 民间项目，前提是不对古书馆造成负面影响，不干扰日常维护施工，不破坏各方关系或造成麻烦。
    - 编辑组目前通过 QQ 群沟通，配合腾讯文档进行日常施工和维护。

    ### 关于申请表
    - 长期开放，特殊情况可能暂时关闭。
    - 信息仅供组内了解基本情况和后续配合，不会以任何形式外传。把它当作学校社团的小测试，没有标准答案，自由发挥即可。
    - 每人仅一份有效申请，重复提交无效，结果不能修改，请思考后提交。特殊情况可联系频道或值班室。
    - 提交后一周内完成审核，通过者将收到邮件邀请。
    - 一周后未收到联系或邮件，代表此次申请未通过。这并非否定您的能力，仍可通过组外反馈收集表帮助古书馆。
    """
        .trimIndent()

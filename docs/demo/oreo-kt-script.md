<!--
Speaker script for the onboarding KT (Chinese, for the presenter), page by page with docs/demo/oreo-kt-deck.html.
Day 1: Genie itself, then how oreo-e2e uses it. Day 2: test data and the AI workflow.
[翻页 N] marks the slide number; [操作] is what to show on screen; [提问] is a question for the audience.
-->

# OREO E2E 框架 KT 演讲稿

- 第 1 天：第 1–18 页，约 90 分钟（Genie 本身 40 分钟，oreo-e2e 怎么用 Genie 50 分钟）
- 第 2 天：第 19–25 页，约 90 分钟（测试数据 40 分钟，AI 工作流 30 分钟，收尾和答疑 20 分钟）
- 标记：**[翻页 N]** 切到第 N 页；**[操作]** 屏幕上要做的事；**[提问]** 停下来问听众

**讲之前准备：**
1. 在自己机器上跑一次 develop 分支上的最小 Genie 项目，确认和当前 Genie 版本兼容。
2. 试一下最小项目能不能加载 snippet（glue 路径里要包含 snippet 目录）；不行就在第 7 页改用 oreo 的例子。
3. 挑一个简单的 trade_creation API case，第 18 页现场追。提前用 `case` 命令打印一次它的数据。
4. 共享屏幕前关掉 properties 文件，别让真实地址和账号出现在屏幕上。

---

# 第 1 天

## 开场（约 5 分钟）

**[翻页 1] 封面**

> 大家好。今天和下次，我们分两次把 OREO 的 E2E 自动化框架过一遍。
>
> 这套框架规则比较多，第一次看代码很容易迷路：一个 step 不知道最后跑到了哪里，一个 case 不知道数据从哪来。所以这次 KT 的目标不是让大家记住所有规则，而是**建立一张地图**：以后遇到问题，知道该去哪一层找、该看哪份文档。
>
> 两次讲解中间隔一两天，每次讲完都有一个小作业。动手跑一次，比听我讲十遍都有用。有问题随时打断我，不用攒到最后。

**[翻页 2] 日程**

> 两天分四段。
>
> 第一天上半场，我们先**不看** oreo-e2e，而是看一个最小的 Genie 项目，只有五类文件。先搞清楚 Genie 这个框架本身是怎么工作的。
>
> 下半场再看 oreo-e2e：同样的骨架，我们在上面加了什么，为什么要加。最后现场追一个真实的 case。
>
> 第二天讲测试数据，也就是 oreo-e2e 最有特色的部分，然后是 AI 工作流。
>
> 为什么先讲 Genie？因为 oreo-e2e 里很多规则是**我们自己的设计**，不是框架的要求。先看过最小项目，大家就能分清：哪些是 Genie 规定的，哪些是我们为了解决自己的问题加的。后者是可以讨论、可以改进的。

**[翻页 3] 我们测什么，为什么要自动化**

> 讲框架之前，先花五分钟讲清楚它要解决什么问题。
>
> 被测系统是 OREO FX 交易管理门户。我们覆盖 trade 的整个生命周期：创建、审批、修改、取消、提前终止、分配、fixing 等等。
>
> 难点在右边这张卡片：**产品是一个个上线的**。每上一个新产品，它都要在所有 lifecycle event 上回归一遍。产品数乘以事件数，靠手工回归肯定跟不上。
>
> 所以整个框架的核心设计就是下面这句话：**事件是文件，产品是行。** 一个 lifecycle event 对应一个 feature 文件；上线一个新产品，只是在 Examples 里多一行，就能复用所有 event 的 case。
>
> 这句话大家先记住，后面很多规则，比如 Examples 不能手改、要用工具生成，都是从这里来的。第二天会细讲。

---

## 第一段：Genie 本身（约 35 分钟）

**[翻页 4] Genie 是什么**

> Genie 是公司内部基于 Cucumber-JVM 和 JUnit 封装的测试框架。
>
> 打个比方：Cucumber-JVM 是**发动机**，它做的事情很单纯，就是把 feature 里的每一行 step 匹配到一个 step 定义上执行。
>
> Genie 是**整辆车**：它在 Cucumber 外面包了 runner、REST / 数据库 / Playwright 这些模块、大量内置 step、元素 JSON、配置、报告和并行。
>
> 我们的项目是**车上的货**：feature，加上内置 step 做不到时才写的自定义 step。
>
> 这一页只需要记住一句话：**内置 step 能做的，不写 Java。** 内置 step 的目录在 docs 下面这两份 catalog 里，写新步骤之前先去查。

**[翻页 5] 一个最小的 Genie 项目**

> 说概念不如看实物。这是 develop 分支上一个最小的、能跑的 Genie 项目，就这五类文件。
>
> **[操作]** 打开最小项目，按表格顺序逐个文件点开。
>
> - **RestTest.java**，入口类。看它上面的注解：用谁来跑、feature 在哪、glue 在哪。
> - **playwright.feature**，场景。大家注意，里面大部分 step 我们一行 Java 都没写，全是 Genie 内置的。
> - **SampleGlue.java**，自定义 step。一个 `@And` 注解加一个正则，就是一个 step 定义。feature 里有一行文字能匹配上这个正则，就会执行这个方法。
> - **elements 下面的 JSON**，定位器。feature 里只写元素的 key，具体的选择器放在这里。
> - **genie.properties**，配置：用什么浏览器、要不要 headless、超时多长、元素文件在哪。
>
> 就这五类文件，一个 Genie 项目就能跑起来。等一下大家会看到，oreo-e2e 里的每一类文件，在这里都能找到原型。

**[翻页 6] 跑一次，发生了什么**

> **[操作]** 现场跑一次最小项目。
>
> 跑起来的时候发生了四件事：
> 1. **启动**：runner 接管 JUnit，读 genie.properties，加载模块。
> 2. **匹配 step**：feature 的每一行，拿去和内置 step、SampleGlue 的正则逐个匹配。
> 3. **执行**：遇到元素 key，就去 elements JSON 里查定位器，然后驱动浏览器。
> 4. **报告**：每个 step 的结果，失败的时候截图。
>
> **[操作]** 把 feature 里一个 step 故意改错一个字，再跑一次。
>
> 大家看，报错是 step undefined。这是 Genie 层最常见的错误：这行文字没有匹配上任何 step 定义。以后看到这个错误，第一反应就是去查正则和 glue 路径，不用怀疑别的。
>
> 下面这句要注意：这里**还没有** snippet、data 目录、case ID 和并行。这些都是 oreo-e2e 在 Genie 上加的。

**[翻页 7] Snippet：用文本写的 step 定义**

> 刚才的 feature 有个问题：里面都是技术步骤，比如点哪个按钮、填哪个字段。业务的人看不懂，步骤一多也很难维护。
>
> Genie 的解决办法是 snippet：**用文本写的 step 定义**。
>
> 看左边这个例子。第一行是正则，和 Java 里 `@And` 后面的正则是一回事；`$1` 是正则捕获的参数，这里是用户角色，比如 maker、checker。下面三行是展开的步骤，可以是内置 step、别的 snippet 或者 Java glue，一层层展开。
>
> 所以 feature 里只要写一句 `start a new api request as user 'maker'`，背后三个技术步骤都由 snippet 替你做了。
>
> **[操作]** 在最小项目的 feature 里找三行连续的技术步骤，写一个 snippet 把它们合成一行业务语言，再跑一次。
>
> （如果最小项目加载不了 snippet：直接打开 oreo 的 `snippet/api/common_api.snippet`，讲上面这个例子。）
>
> 这就是 oreo-e2e 能把 feature 写成纯业务语言的原因：技术细节全部沉到 snippet 和 glue 里。

**[翻页 8] 同一个骨架，oreo-e2e 多了什么**

> 这一页是两段之间的桥。左边是刚才的最小项目，中间是 oreo-e2e，右边是多出来的东西。
>
> - RestTest 对应 OreoTest，多了固定 tag 和并行配置。
> - SampleGlue 对应 glue 下的 api、ui、hooks 三个包，原则是 Java 只写缺少的能力。
> - feature 里不再写技术步骤，全部下沉到 snippet。
> - elements 按页面和组件拆开。
> - properties 多了一层环境文件。
> - 最后一行，最小项目里**没有**，是 data 目录和 case ID，这是第二天的内容。
>
> **[提问]** 接下来讲的每一条 oreo 规则，大家都可以先问自己一句：这是 Genie 要求的，还是我们自己的设计？

---

## 第二段：oreo-e2e 怎么用 Genie（约 50 分钟）

**[翻页 9] OreoModule：oreo-e2e 怎么接入 Genie**

> 下半场正式进 oreo-e2e。
>
> oreo-e2e 有自己的模块，叫 OreoModule。它做两件事：声明依赖哪些 Genie 模块，以及 glue 从哪里找。
>
> **[操作]** 打开 OreoModule.java，看 getDependencies 和 getGluePaths 两个方法。
>
> 依赖的模块就是右边这五个：Spring、Rest、Jdbc、Sql、Database，Genie 按顺序加载，每个模块都带来自己的内置 step。
>
> glue 路径里有两个来源：一个是 Java 包，一个是 `classpath:snippet`。snippet 能被找到，就是因为这里注册了它。

**[翻页 10] 入口：OreoTest，对照 RestTest 看**

> **[操作]** 打开 OreoTest.java，和最小项目的 RestTest 并排放。
>
> 三个注解：
> - `@RunWith(Genie.class)`：由 Genie 接管 JUnit。所以 `mvn test -Dtest=OreoTest` 跑的就是 Genie。
> - `@CucumberOptions`：feature 在哪、glue 在哪、默认 tag。
> - `@GenieOptions`：用哪个环境配置文件、怎么并行。默认 qa.properties，FEATURE 模式，3 个并发。
>
> 下面这条一定要记住：OreoTest 有**固定 tag**，`not @wip` 和 `@ADO#trade_creation`。命令行传的 filter 会和它们叠加。所以 `mvn test` **不是全量**；跑出 0 个 case 的时候，先查 tag，不要以为全部通过了。
>
> 选 case 只用 `-Dcucumber.options`，不要用 `-Dcucumber.filter.*`，这里不支持，可能被悄悄忽略，结果跑了全部。
>
> 顺便提一句：仓库里还有一个 SeedTest，它是给 UAT 批量下 SIT seed 用的，不是测试，`mvn test` 也不会跑它。

**[翻页 11] 一张图看懂分层**

> 这是整个框架最重要的一张图。
>
> 一个 case 从业务描述到真正调用系统，经过四层：feature、snippet、glue、API 或浏览器。越往左越接近业务语言，越往右越接近技术实现。
>
> 规则只有一条：**技术细节只能往右沉。** endpoint、请求体、选择器，都不能出现在 feature 里。
>
> 下面两类数据不在这条链上，但各层都会用到：data 目录放请求体和录制的期望响应，按 case ID 取数；elements 目录放 UI 定位器，选择器不进 feature，也不写死在 Java 里。

**[翻页 12] 目录地图**

> **[操作]** 打开 IDE，对照这张表逐行点一遍目录。
>
> 重点说三处：
> - features 按领域分目录，**一个 event 一个 feature**，这就是“事件是文件”。
> - UI 的 snippet 分三层：flows 是业务流程，pages 是页面上的原子操作，components 是多个页面共用的弹窗、提示框之类。
> - snippet 和 glue 在 src/main，features、data、elements、properties 在 src/test/resources。
>
> 以后找不到东西，先回到这张表。

**[翻页 13] API 和 UI 各测什么**

> 同一个需求，放 API 测还是 UI 测？
>
> **@api 测业务规则**：字段校验、状态流转、审批、返回结果。又快又稳，是覆盖的主力。
>
> **@playwright 测用户能不能完成操作**：页面流程、提示信息、blotter 里的展示。只验证关键路径。
>
> 判断方法很简单，拿到一个验收标准先问：这是业务规则，还是用户操作？
>
> 还有一点：UI case 的前置条件通过 API 准备。比如 `a Live '<product_type>' trade exists in the blotter`，它在后台用 API 下好一个 trade，不登录、不导航，trade ID 存在 createdTradeId 里。但 UI scenario 不能直接调用 API 的 snippet。
>
> 详细的分层规则在 docs/standards/test-layering-standards.md。

**[翻页 14] 改动放在哪：先复用，最后才写 Java**

> 要加一个新步骤的时候，按这个顺序往下找，遇到第一个合适的就停：
> 1. 已有的 flow、page、component 或 API snippet 能表达，直接复用。
> 2. Genie 内置 step 能做到，在 snippet 里用。
> 3. 已有的 glue 能做到，在 snippet 里调用。
> 4. 用上面这些组合出一个新 snippet。
> 5. 都做不到，才写新的 Java，而且一次只加一个能力。
>
> 大家会发现，这就是我们在最小项目里讲的“内置 step 能做的，不写 Java”的完整版。
>
> 提醒一下：老代码里有一些 wrapper、Thread.sleep、直接用 RestAssured 的写法。不要模仿，也不要在做别的改动时顺手重构。

**[翻页 15] 一个 scenario 的生命周期**

> 一个 scenario 从头到尾经过五步：启动与筛选、@Before 准备、逐个执行 step、@After 收尾、出报告。
>
> 和最小项目比，多了 hooks：失败时截图、保存 Playwright trace。trace 的保留方式在 genie.properties 里配，默认是失败时保留。
>
> 下面两条对写 case 很重要：
> - **scenario 之间不共享状态**：每个 scenario 都会创建新的 glue 实例，不要指望上一个 scenario 留下什么。
> - **step 之间靠 stored variable 传值**：比如前一步把 trade ID 存进 createdTradeId，后一步用 `from stored variable 'createdTradeId'` 取出来。

**[翻页 16] 配置与环境**

> 配置分两层。genie.properties 放公共默认值：浏览器、headless、超时、trace 模式、risk engine 用 MOCK、元素文件路径。
>
> 每个环境一个 properties 文件：dev、sit、qa、uat、pt，放地址和账号。默认用 qa，切环境就加这个参数。
>
> **[操作]** 可以打开 genie.properties 看配置项，**不要**打开环境文件。
>
> 规则：地址和账号只能放在 properties 里，不能写进代码或 feature，也不能提交。账号按角色通过 config/TestUser 获取。

**[翻页 17] 并行：多进程，不是多线程**

> 最后一个机制是并行。
>
> 为什么是多进程？因为 Cucumber-JVM 不是线程安全的。master 进程把 feature 切成工作单元排队，三个子 JVM 各领一个 feature 串行跑，一个跑完马上补下一个。
>
> 这对写 case 有两个要求：
> - 每个子进程有独立的临时目录，读文件必须走 classpath，**不能用相对路径**。
> - 一个 case 不能依赖别的 feature 会修改的数据，比如一个固定的 trade 或者某个用户设置。单独跑能过、一起跑就挂，多半是这个原因。
>
> 并发数也不是越高越快：每个子进程都是一个完整的 JVM，UI case 还要起自己的浏览器。细节在 docs/genie-parallel-execution.md。

**[翻页 18] 现场演示：从头到尾追一个 case**（留足 15 分钟）

> 今天最后一部分，也是最重要的部分。我们把今天讲的东西串起来，追一个真实的 case。
>
> **[操作]** 按页面上的四步现场做：
> 1. **找到 scenario**：在 features/api 里搜 case ID，它写在 scenario 名字里。
> 2. **跳到 snippet**：从 feature 的一行 step 跳进 snippet，一层层展开，直到落到一个内置 step 或 Java 方法。边跳边说：这一层是 Genie 的，这一层是我们的。
> 3. **打印数据**：用 `case` 命令看这个 case 最终发出去的请求和比对的期望响应。数据是怎么拼出来的，下次讲。
> 4. **跑一次**：用最窄的 filter，只跑这一个 case。
>
> 这里强调一句：**API 运行会下真实的 trade**。所以永远只用最窄的 filter，不要随手跑全量。

**第 1 天结束语**

> 今天就到这里。我们从一个最小的 Genie 项目出发，看了 Genie 怎么跑；然后看了 oreo-e2e 在同一个骨架上加了什么：模块、snippet、分层、配置和并行。
>
> 下次之前有一个小作业：
> 1. 在自己机器上把最小 Genie 项目跑通。
> 2. 在 oreo-e2e 里自己追一个 API case 和一个 UI case，把展开链路画出来。
>
> 下次开场请你花五分钟讲给我们听。遇到卡住的地方随时问我，不用攒着。谢谢大家。

---

# 第 2 天

## 开场（约 10 分钟）

> 欢迎回来。先请 [新同事] 用五分钟讲一下上次追的那两个 case，展开链路是什么样的，中间有没有卡住的地方。
>
> （听完后）很好。上次结束时那张对照表的最后一行，最小项目里“没有”的那部分，就是今天的主题：**测试数据**。讲完数据，再讲我们怎么用 AI 来写和跑这些 case。

## 第三段：测试数据（约 35 分钟）

**[翻页 19] Case ID 是数据的钥匙**

> 还记得第一天开场那句话吗？**事件是文件，产品是行。** 把这句话落到代码里，靠的就是 case ID。
>
> 拆开看 `TC-TRADE-CREATION-FX_TRF-API-001`：
> - `TRADE-CREATION` 是 event，决定读哪个数据文件，这里是 data/api/trade_creation.json。
> - `FX_TRF` 是产品。
> - `API` 是层。
> - `001` 是编号，对应数据文件里的那一行。
>
> 三个容易错的地方：
> - case ID 写在 scenario **名字**里，**不是 tag**。
> - 产品是 composition，比如 FX_CO，不是 product ID，比如 FX_PSCRIPT。
> - 没有产品段的 ID，就是不分产品的 case。

**[翻页 20] 四层合并，后面覆盖前面**

> 一个 case 最终发出去的请求体，是四层数据合并出来的：
> 1. defaults，所有 case 共用；
> 2. byProduct，这个产品不同的地方；
> 3. case 的行，这个 case 自己的数据；
> 4. 行里的 byProduct，这个 case 在这个产品上的差异。
>
> 后面的覆盖前面的。合并规则只有三条：对象按 key 合并；数组和值整体替换，没有“追加”；null 表示删除这个字段。
>
> 这个设计带来的好处是：**一行数据只为“和默认值不一样”的 case 存在**。正向 case 用默认值就够了，一行都不用写。新上一个产品，大部分 case 只要在 byProduct 里补上它的差异就行。
>
> **[操作]** 打开 trade_creation.json，指出四层分别在哪；再用 `case` 命令打印一个 case，对照看合并结果。

**[翻页 21] 录制、同步与离线检查**

> 期望响应不是手写的，是录出来的。
>
> - 一个新 case 第一次跑，没有录制，框架会把响应写进 src，然后**失败一次**。这是故意的：逼你看一眼录下来的东西再跑第二次。
> - 每次都会变的字段，比如时间戳、ID，写 dynamicFields 规则 mask 掉，再 compact。
> - 新产品或新 capability，在 ProductDatFiles 注册，再用 sync-examples 生成 Examples。
> - 提交之前，跑一次离线检查，不连环境也能发现数据问题。
>
> 这一页最重要的是下面这句：**录制展示的是系统做了什么，不是系统应该做什么。** 录下来的响应和需求对不上时，先停下来问，不要直接接受。
>
> 所以期望文件和 Examples 都**不能手改**。提交前看一遍 git diff，确认没有真实 trade ID、地址或账号。
>
> （更细的讲解顺序和常见坑，可以配合 docs/demo/oreo-e2e-demo.html。）

## 第四段：AI 工作流（约 30 分钟）

**[翻页 22] 两条流水线，每一步都有人审批**

> 前面讲的所有规则，我们都写进了仓库，AI agent 也按这些规则干活。现在有两条流水线。
>
> **第一条，/writebddfeatures**：输入一个 user story，输出 feature 文件。先拆成测试点，决定每个点放 API 还是 UI；人审批；再写成业务语言的 Gherkin；人再审批。
>
> **第二条，/codetestcases**：输入 feature，输出能跑的 case。补齐数据、snippet、glue；做离线检查和 dry run，这一步不下 trade；人审批之后，才用最窄的 filter 真实运行。
>
> 跑出来的如果是产品缺陷，用 qa-defect-reporting 起草 ADO Bug，确认之后才创建。
>
> **[操作]** 现场拿一个准备好的 story 走一遍第一条流水线。如果现场环境不顺，切到提前准备的截图。
>
> 最后强调一点：AI 生成的内容和人写的一样，要经过审批和 code review。它省掉的是重复劳动，不是判断。
>
> （交接物、失败分流和完整例子，见 docs/demo/oreo-ai-workflow.html。）

## 收尾（约 20 分钟）

**[翻页 23] 五条红线**

> 两天讲了很多规则。如果只能记五条，就记这五条，这是新人最容易出事的地方：
> 1. API 运行会下**真实的 trade**，只用最窄的 filter。
> 2. mvn test 不是全量：有固定 tag，选 case 用 -Dcucumber.options。
> 3. Examples 和期望响应**不能手改**。
> 4. feature 里不出现 endpoint、请求体和选择器。
> 5. 不提交账号、密码、环境地址和真实 trade ID。
>
> 其余规则在 CLAUDE.md 第 4 节，自己读一遍。

**[翻页 24] 出问题时，先查哪一层**

> 最后给一张排查表，出问题时先对照：
> - step undefined：查 snippet 的正则和 glue 路径。第一天我们现场造过这个错误。
> - 跑了 0 个 case：filter 和固定 tag 叠加后是不是空的。
> - 单独能过、并行时失败：有没有共享状态，有没有用相对路径。
> - 连不上或者 401：用的是哪个 properties，账号对不对。
> - 和期望响应对不上：是动态字段没有 mask，还是系统行为真的变了。
>
> 最后一种最需要判断力：如果是系统行为变了，要对照需求，确认是缺陷还是需求变更，**不能直接重新录制**。

**[翻页 25] 前两周要做的事**

> 接下来两周，四件事：
> 1. **读规则**：CLAUDE.md 和 docs/standards 下的三份标准。
> 2. **配环境**：先跑通最小 Genie 项目，再在公司机器上跑通离线检查。
> 3. **追链路**：上次的作业，继续多追几个 case，API 和 UI 都要有。
> 4. **挑问题**：记下文档和代码对不上的地方。以代码为准，再去修文档。
>
> 第四件事我特别想说一下：新人的眼睛是最好的检查工具。我们天天用，已经看不出哪里写得不清楚了。你发现的每一处不一致，都是对这个项目的贡献。
>
> 两天的 KT 就到这里。有任何问题随时找我，不用攒着。谢谢大家。

---

## 附：可能被问到的问题

| 问题 | 回答要点 |
|---|---|
| 为什么不直接用 Cucumber，要套一层 Genie？ | 模块、内置 step、元素 JSON、并行和报告都是现成的，项目只写业务步骤。 |
| snippet 和 Java glue 怎么选？ | 按第 14 页的顺序：能组合就组合，最后才写 Java。 |
| 为什么 Examples 不能手改？ | 产品是行，由 sync-examples 根据产品注册生成；手改很容易和注册表不一致，下次同步还会被覆盖。 |
| 录下来的响应能直接用吗？ | 先对照需求看一遍。录制是系统做了什么，不是应该做什么。 |
| AI 写的 case 可靠吗？ | 每一步都有人审批，真实运行前有离线检查和 dry run，和人写的一样走 code review。 |

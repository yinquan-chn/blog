# AQL 文档

>AQL是ArangoDB的查询语言，它允许在ArangoDB中用各种方式存储，检索和修改数据。

AQL是一个声明式语言，也就是说，AQL表达的是要得到什么结果，但不表达如何得到结果。AQL的目标是易读易懂，因此它使用了英语的关键词。AQL的设计目标之一就是客户端独立性，即语言和语法都是一样的，不管客户端使用的是什么编程语言。AQL的设计目标之二就是支持复杂查询模式，以及ArangoDB提供的各种数据模型。

在其目的上，AQL类似于结构化查询语言（SQL）。AQL支持读取和修改集合数据，但不支持数据定义操作，如创建和删除数据库，集合和索引。AQL是一种纯数据操作语言（DML），而不是数据定义语言（DDL）或数据控制语言（DCL）。

AQL查询的语法与SQL不同，尽管某些关键字重叠。不过对于有SQL背景的用户而言，AQL应该是容易理解的。

执行查询时通常的工作流程如下：

1. 客户端程序将AQL查询发送到ArangoDB服务器。查询文本中包含了ArangoDB需要的所有内容，以便计算结果集。

2. ArangoDB解析查询，执行它，并编译结果。如果查询无效或无法执行，服服务器将返回客户端可以处理和响应的错误。如果查询可以成功执行，服务器将查询结果（如果有）返回给客户端。

## 一、如何执行AQL查询
AQL查询可以通过以下方式执行：
- 通过web接面
- 使用JavaScript API的数据库对象，例如，在arangosh或Foxx服务中
- 通过原始REST HTTP API

他们背后都是调用服务器的HTTP API，但web接口和数据库对象抽象了低级通信细节而且易于使用。

ArangoDB web界面有特定的[查询区域](#Executing_AQL_queries_in_the_ArangoDB_web_interface)。

你可以[在ArangoDB Shell运行AQL查询](https://docs.arangodb.com/3.11/aql/how-to-invoke-aql/with-the-web-interface/)使用[db._query()](https://docs.arangodb.com/3.11/aql/how-to-invoke-aql/with-arangosh/#with-db_query)和[db._createStatement()](https://docs.arangodb.com/3.11/aql/how-to-invoke-aql/with-arangosh/#with-db_createstatement-arangostatement)的db对象。本章也描述了如何使用绑定参数，统计，计数和游标与arangosh。

如果你使用Foxx microservices，查看[如何编写数据库查询](https://docs.arangodb.com/3.11/develop/foxx-microservices/getting-started/#writing-database-queries)；例如，包括标记的模板字符串

如果你希望使用HTTP REST API运行AQL查询，可以查看AQL查询的[HTTP接口描述](https://docs.arangodb.com/3.11/develop/http-api/queries/aql-queries/)

### 2.在ArangoDB web界面中执行AQL查询
<a id="Executing_AQL_queries_in_the_ArangoDB_web_interface" ></a>

> 你可以使用web界面中的查询编辑器运行特殊AQL查询

在web界面的查询区域中，输入一个查询，然后点击执行按钮，查询结果将出现在下面的编辑器中。

编辑器提供了一些可以用作模板的示例查询。它还提供了一个功能，可以通过单击“Explain”按钮来解释查询并检查其执行计划。

绑定参数可以在右侧窗格中定义。该格式与HTTP REST API和（JavaScript）应用程序代码中用于绑定参数的格式相同。

```aql
FOR doc IN @@collection
  FILTER CONTAINS(LOWER(doc.author), @search, false)
  RETURN { "name": doc.name, "descr": doc.description, "author": doc.author }
```
用来绑定的json参数
```json
{
    "@collection": "_apps",
    "search": "arango"
}
```
How bind parameters work can be found in AQL Fundamentals.
绑定参数如何工作可以参考[AQL 基础](#AQL_Fundamentals)

查询也可以与它们的绑定参数值一起保存在AQL编辑器中，以便以后重用。这些数据存储在当前数据库的用户配置文件中（在_users系统集合中）。

Also see the detailed description of the Web Interface.
也可以查看[web界面的详细描述](https://docs.arangodb.com/3.11/components/web-interface/)

## AQL 基础
<a id="AQL_Fundamentals" ></a>

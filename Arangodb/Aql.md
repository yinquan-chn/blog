# AQL 文档

>AQL是ArangoDB的查询语言，它允许在ArangoDB中用各种方式存储，检索和修改数据。

AQL是一个声明式语言，也就是说，AQL表达的是要得到什么结果，但不表达如何得到结果。AQL的目标是易读易懂，因此它使用了英语的关键词。AQL的设计目标之一就是客户端独立性，即语言和语法都是一样的，不管客户端使用的是什么编程语言。AQL的设计目标之二就是支持复杂查询模式，以及ArangoDB提供的各种数据模型。

在其目的上，AQL类似于结构化查询语言（SQL）。AQL支持读取和修改集合数据，但不支持数据定义操作，如创建和删除数据库，集合和索引。AQL是一种纯数据操作语言（DML），而不是数据定义语言（DDL）或数据控制语言（DCL）。

The syntax of AQL queries is different to SQL, even if some keywords overlap. Nevertheless, AQL should be easy to understand for anyone with an SQL background.
AQL查询的语法与SQL不同，尽管某些关键字重叠。不过对于有SQL背景的用户而言，AQL应该是容易理解的。

The general workflow when executing a query is as follows:

A client application ships an AQL query to the ArangoDB server. The query text contains everything ArangoDB needs to compute the result set.
ArangoDB parses the query, executes it, and compiles the results. If the query is invalid or cannot be executed, the server returns an error that the client can process and react to. If the query can be executed successfully, the server returns the query results (if any) to the client.
For example queries, see the Data Queries and Examples & Query Patterns chapters.
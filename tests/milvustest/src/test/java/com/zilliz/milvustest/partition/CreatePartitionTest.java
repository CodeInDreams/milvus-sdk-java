package com.zilliz.milvustest.partition;

import com.zilliz.milvustest.common.BaseTest;
import com.zilliz.milvustest.common.CommonData;
import com.zilliz.milvustest.common.CommonFunction;
import com.zilliz.milvustest.util.MathUtil;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.partition.CreatePartitionParam;
import io.milvus.param.partition.DropPartitionParam;
import io.milvus.response.QueryResultsWrapper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Epic("Partition")
@Feature("CreatePartition")
public class CreatePartitionTest extends BaseTest {
  private String partition;

  @BeforeClass(description = "init partition Name")
  public void createPartitionTest() {
    partition = "partition_" + MathUtil.getRandomString(10);
  }

  @AfterClass(description = "delete partition after test")
  public void deletePartition() {
    milvusClient.dropPartition(
        DropPartitionParam.newBuilder()
            .withCollectionName(CommonData.defaultCollection)
            .withPartitionName(partition)
            .build());
  }

  @Severity(SeverityLevel.BLOCKER)
  @Test(description = "Create partition")
  public void createPartitionSuccess() {
    CreatePartitionParam createPartitionParam =
        CreatePartitionParam.newBuilder()
            .withCollectionName(CommonData.defaultCollection)
            .withPartitionName(partition)
            .build();
    R<RpcStatus> rpcStatusR = milvusClient.createPartition(createPartitionParam);
    Assert.assertEquals(rpcStatusR.getStatus().intValue(), 0);
    Assert.assertEquals(rpcStatusR.getData().getMsg(), "Success");
  }

  @Test(
      description = "query float vector from partition ",
      dependsOnMethods = "createPartitionSuccess")
  @Severity(SeverityLevel.NORMAL)
  public void queryFromEmptyPartition() {
    milvusClient.loadCollection(
        LoadCollectionParam.newBuilder().withCollectionName(CommonData.defaultCollection).build());
    String SEARCH_PARAM = "book_id in [2,4,6,8]";
    List<String> outFields = Arrays.asList("book_id", "word_count");
    List<String> partitions =
        new ArrayList<String>() {
          {
            add(partition);
          }
        };
    QueryParam queryParam =
        QueryParam.newBuilder()
            .withCollectionName(CommonData.defaultCollection)
            .withPartitionNames(partitions)
            .withOutFields(outFields)
            .withExpr(SEARCH_PARAM)
            .build();
    R<QueryResults> queryResultsR = milvusClient.query(queryParam);
    Assert.assertEquals(queryResultsR.getStatus().intValue(), 26);
    Assert.assertEquals(queryResultsR.getException().getMessage(), "emptly collection");
  }

  @Test(
      description = "query float vector from partition AfterInsertData",
      dependsOnMethods = {"createPartitionSuccess","queryFromEmptyPartition",})
  @Severity(SeverityLevel.NORMAL)
  public void queryFromPartitionAfterInsertData() throws InterruptedException {
    List<InsertParam.Field> fields = CommonFunction.generateData(100);
    milvusClient.insert(
        InsertParam.newBuilder()
            .withCollectionName(CommonData.defaultCollection)
            .withPartitionName(partition)
            .withFields(fields)
            .build());
    milvusClient.loadCollection(
        LoadCollectionParam.newBuilder().withCollectionName(CommonData.defaultCollection).withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingInterval(1000L).withSyncLoadWaitingTimeout(300L).build());
    Thread.sleep(5000);
    String SEARCH_PARAM = "book_id in [2,4,6,8]";
    List<String> outFields = Arrays.asList("book_id", "word_count");
    List<String> partitions =
        new ArrayList<String>() {
          {
            add(partition);
          }
        };
    QueryParam queryParam =
        QueryParam.newBuilder()
            .withCollectionName(CommonData.defaultCollection)
            .withPartitionNames(partitions)
            .withOutFields(outFields)
            .withExpr(SEARCH_PARAM)
            .build();
    R<QueryResults> queryResultsR = milvusClient.query(queryParam);
    QueryResultsWrapper wrapperQuery = new QueryResultsWrapper(queryResultsR.getData());
    System.out.println(wrapperQuery.getFieldWrapper("book_id").getFieldData());
    System.out.println(wrapperQuery.getFieldWrapper("word_count").getFieldData());
    Assert.assertEquals(queryResultsR.getStatus().intValue(), 0);
    Assert.assertEquals(wrapperQuery.getFieldWrapper("word_count").getFieldData().size(), 4);
    Assert.assertEquals(wrapperQuery.getFieldWrapper("book_id").getFieldData().size(), 4);
  }
}
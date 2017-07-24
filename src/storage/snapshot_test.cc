//
// segment_test.cc
// Copyright (C) 2017 4paradigm.com
// Author wangtaize 
// Date 2017-03-31
//

#include "gtest/gtest.h"
#include "logging.h"
#include "storage/snapshot.h"
#include "storage/table.h"
#include "storage/ticket.h"
#include "proto/tablet.pb.h"
#include <boost/lexical_cast.hpp>
#include <iostream>
#include <sched.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <gflags/gflags.h>

DECLARE_string(snapshot_root_path);
using ::rtidb::api::LogEntry;
namespace rtidb {
namespace storage {

class SnapshotTest : public ::testing::Test {

public:
    SnapshotTest(){}
    ~SnapshotTest() {}
};

inline std::string GenRand() {
    return boost::lexical_cast<std::string>(rand() % 10000000 + 1);
}

TEST_F(SnapshotTest, Onebox) {
    FLAGS_snapshot_root_path = "/tmp/rtidb/snapshot/" + GenRand();
    Snapshot* snapshot = new Snapshot(1, 1, 0);
    snapshot->Ref();
    bool ok = snapshot->Init();
    ASSERT_TRUE(ok);
    {
        LogEntry entry;
        entry.set_pk("test0");
        entry.set_value("value0");
        entry.set_ts(9637);
        entry.set_log_index(1);
        std::string raw;
        entry.SerializeToString(&raw);
        ok = snapshot->Put(raw, entry.log_index(), "test0", 9637);
        ASSERT_TRUE(ok);
    }
    {
        LogEntry entry;
        entry.set_pk("test0");
        entry.set_value("value1");
        entry.set_ts(9638);
        entry.set_log_index(2);
        std::string raw;
        entry.SerializeToString(&raw);
        ok = snapshot->Put(raw, entry.log_index(), "test0", 9638);
        ASSERT_TRUE(ok);
    }
    {
        LogEntry entry;
        entry.set_pk("test1");
        entry.set_value("value2");
        entry.set_ts(9638);
        entry.set_log_index(3);
        std::string raw;
        entry.SerializeToString(&raw);
        ok = snapshot->Put(raw, entry.log_index(), "test1", 9638);
        ASSERT_TRUE(ok);
    }
    {
        DeleteEntry entry;
        entry.pk = "test0";
        entry.ts = 9638;
        std::vector<DeleteEntry> entries;
        entries.push_back(entry);
        snapshot->BatchDelete(entries);
    }
    snapshot->UnRef();
    snapshot = new Snapshot(1, 1, 0);
    snapshot->Ref();
    ok = snapshot->Init();
    ASSERT_TRUE(ok);
    ASSERT_EQ(3, snapshot->GetOffset());
    Table* table = new Table("test", 1, 1, 8, 0, false);
    table->Ref();
    table->Init();
    ok = snapshot->Recover(table);
    ASSERT_TRUE(ok);
    {
        Ticket ticket;
        Table::Iterator* it = table->NewIterator("test0", ticket);
        it->SeekToFirst();
        ASSERT_TRUE(it->Valid());
        DataBlock* value = it->GetValue();
        std::string value_str(value->data, value->size);
        ASSERT_EQ("value0", value_str);
        ASSERT_EQ(9637, it->GetKey());
        it->Next();
        ASSERT_FALSE(it->Valid());
        delete it;
    }
    {
        Ticket ticket;
        Table::Iterator* it = table->NewIterator("test1", ticket);
        it->SeekToFirst();
        ASSERT_TRUE(it->Valid());
        DataBlock* value = it->GetValue();
        std::string value_str(value->data, value->size);
        ASSERT_EQ("value2", value_str);
        ASSERT_EQ(9638, it->GetKey());
        it->Next();
        ASSERT_FALSE(it->Valid());
        delete it;
    }

}

}
}

int main(int argc, char** argv) {
    srand (time(NULL));
    ::baidu::common::SetLogLevel(::baidu::common::DEBUG);
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}



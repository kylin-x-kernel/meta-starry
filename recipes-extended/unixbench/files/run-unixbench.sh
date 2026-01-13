#!/bin/sh
# UnixBench runner for embedded systems 

BENCH_DIR=@DATADIR@/unixbench
PGMS_DIR=$BENCH_DIR/pgms
RESULTS_DIR=$BENCH_DIR/results
TMP_DIR=$BENCH_DIR/tmp

echo "========================================"
echo "UnixBench Test Suite"
echo "========================================"

cd $BENCH_DIR || exit 1

# 创建结果文件
RESULT_FILE=$RESULTS_DIR/result-$(date +%Y%m%d-%H%M%S).txt
echo "UnixBench Results - $(date)" > $RESULT_FILE
echo "========================================" >> $RESULT_FILE

run_test() {
    local test_name=$1
    local test_bin=$2
    local test_args=$3
    
    echo ""
    echo "Running: $test_name..."
    
    if [ ! -x "$PGMS_DIR/$test_bin" ]; then
        echo "  SKIP: $test_bin not found"
        echo "$test_name: SKIPPED" >> $RESULT_FILE
        return
    fi
    
    # 运行测试并记录结果
    if $PGMS_DIR/$test_bin $test_args > $TMP_DIR/$test_name.out 2>&1; then
        echo "  PASS"
        echo "$test_name: PASS" >> $RESULT_FILE
        cat $TMP_DIR/$test_name.out >> $RESULT_FILE
    else
        echo "  FAIL (exit code: $?)"
        echo "$test_name: FAIL" >> $RESULT_FILE
    fi
}

# 运行基准测试（选择 StarryOS 支持的子集）
run_test "dhrystone" "dhry2reg" "10"
run_test "whetstone" "whetstone-double" ""
run_test "syscall" "syscall" "10"
run_test "pipe" "pipe" "10"
run_test "context1" "context1" "10"
run_test "spawn" "spawn" "10"
run_test "execl" "execl" "10"

echo ""
echo "========================================"
echo "Test Complete"
echo "Results saved to: $RESULT_FILE"
echo "========================================"

echo "System Benchmarks Index Score: N/A (individual tests completed)"

cat $RESULT_FILE


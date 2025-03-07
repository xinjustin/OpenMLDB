# Copyright 2021 4Paradigm
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

if(TESTING_ENABLE)
    add_library(mini_cluster_bm_common STATIC mini_cluster_bm.cc)
    add_dependencies(mini_cluster_bm_common openmldb_proto hybridse_proto)

    add_library(base_test STATIC sql_sdk_base_test.cc ../test/base_test.cc)
    add_dependencies(base_test openmldb_proto hybridse_proto)

    set(HYBRIDSE_CASE_LIBS base_test yaml-cpp)

    add_executable(db_sdk_test db_sdk_test.cc)
    target_link_libraries(db_sdk_test ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS} benchmark_main benchmark gtest)

    add_executable(sql_router_test sql_router_test.cc)
    target_link_libraries(sql_router_test ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS} benchmark_main benchmark gtest)

    add_executable(sql_standalone_sdk_test sql_standalone_sdk_test.cc)
    target_link_libraries(sql_standalone_sdk_test gtest ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS})

    add_executable(sql_sdk_test sql_sdk_test.cc)
    target_link_libraries(sql_sdk_test gtest ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS})

    add_executable(sql_cluster_test sql_cluster_test.cc)
    target_link_libraries(sql_cluster_test gtest ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS})

    add_executable(sql_request_row_test sql_request_row_test.cc)
    target_link_libraries(sql_request_row_test ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS} ${ZETASQL_LIBS} benchmark_main benchmark gtest)

    add_executable(mini_cluster_batch_bm mini_cluster_batch_bm.cc)
    target_link_libraries(mini_cluster_batch_bm mini_cluster_bm_common benchmark_main benchmark gtest ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS})

    add_executable(mini_cluster_request_batch_bm mini_cluster_request_batch_bm.cc)
    target_link_libraries(mini_cluster_request_batch_bm mini_cluster_bm_common benchmark_main benchmark gtest ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS})

    add_executable(mini_cluster_request_bm mini_cluster_request_bm.cc)
    target_link_libraries(mini_cluster_request_bm mini_cluster_bm_common benchmark_main benchmark gtest ${BIN_LIBS} ${HYBRIDSE_CASE_LIBS})
endif()

set(SDK_LIBS openmldb_sdk openmldb_catalog client zk_client schema openmldb_flags openmldb_codec openmldb_proto base hybridse_sdk zookeeper_mt ${VM_LIBS} ${LLVM_LIBS} ${BRPC_LIBS} ${ZETASQL_LIBS})

if(SQL_PYSDK_ENABLE)
    find_package(Python3 COMPONENTS Interpreter Development)
    set_property(SOURCE sql_router_sdk.i PROPERTY CPLUSPLUS ON)
    if (APPLE)
        set_property(SOURCE sql_router_sdk.i PROPERTY COMPILE_OPTIONS -python)
    else ()
        set_property(SOURCE sql_router_sdk.i PROPERTY COMPILE_OPTIONS -py3)
    endif ()
    set(UseSWIG_TARGET_NAME_PREFERENCE STANDARD)
    swig_add_library(sql_router_sdk
            TYPE SHARED
            LANGUAGE python
            OUTPUT_DIR ${CMAKE_BINARY_DIR}/sql_pysdk/openmldb
            SOURCES sql_router_sdk.i)
    target_include_directories(sql_router_sdk PRIVATE ${Python3_INCLUDE_DIRS})
    target_link_libraries(sql_router_sdk ${SDK_LIBS})
    if(APPLE)
      set_target_properties(sql_router_sdk PROPERTIES
               SUFFIX ".so")
      set_property(TARGET sql_router_sdk APPEND PROPERTY
        LINK_FLAGS "-flat_namespace -undefined suppress")
    endif()

    # Find if python module MODULE_NAME is available,
    # if not install it to the Python user install directory.
    function(search_python_module MODULE_NAME)
        execute_process(
            COMMAND ${Python3_EXECUTABLE} -c "import ${MODULE_NAME}; print(${MODULE_NAME}.__version__)"
            RESULT_VARIABLE _RESULT
            OUTPUT_VARIABLE MODULE_VERSION
            ERROR_QUIET
            OUTPUT_STRIP_TRAILING_WHITESPACE
            )
        if(${_RESULT} STREQUAL "0")
            message(STATUS "Found python module: ${MODULE_NAME} (found version \"${MODULE_VERSION}\")")
        else()
            message(WARNING "Can't find python module \"${MODULE_NAME}\", user install it using pip...")
            execute_process(
                COMMAND ${Python3_EXECUTABLE} -m pip install --upgrade --user ${MODULE_NAME}
                OUTPUT_STRIP_TRAILING_WHITESPACE
                )
        endif()
    endfunction()
    # Look for required python modules
    search_python_module(setuptools)
    search_python_module(wheel)

    if(APPLE)
        add_custom_target(sqlalchemy_openmldb DEPENDS sql_router_sdk
                COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/sqlalchemy_openmldb/__init__.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/__init__.py
                COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/sqlalchemy_openmldb/saopenmldb.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/saopenmldb.py
                COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/sqlalchemy_openmldb/openmldbapi/_openmldbapi.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi/_openmldbapi.py
                COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/sqlalchemy_openmldb/openmldbapi/__init__.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi/__init__.py
                COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/openmldb/driver.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi/driver.py
                COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:sql_router_sdk> ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi
                COMMAND ${CMAKE_COMMAND} -E copy  ${CMAKE_BINARY_DIR}/sql_pysdk/openmldb/sql_router_sdk.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi
                # Do not strip library for MacOS, refer to https://github.com/4paradigm/OpenMLDB/issues/905
                COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/setup.py ${CMAKE_BINARY_DIR}/python/
                COMMAND ${CMAKE_COMMAND} -E remove_directory dist
                COMMAND cd ${CMAKE_BINARY_DIR}/python/ && ${Python3_EXECUTABLE} setup.py bdist_wheel
                BYPRODUCTS
                python/sqlalchemy-openmldb
                python/build
                python/dist
                python/sqlalchemy-openmldb.egg-info
                )
    else()
        add_custom_target(sqlalchemy_openmldb DEPENDS sql_router_sdk
            COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/sqlalchemy_openmldb/__init__.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/__init__.py
            COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/sqlalchemy_openmldb/saopenmldb.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/saopenmldb.py
            COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/sqlalchemy_openmldb/openmldbapi/_openmldbapi.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi/_openmldbapi.py
            COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/sqlalchemy_openmldb/openmldbapi/__init__.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi/__init__.py
            COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/openmldb/driver.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi/driver.py
            COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:sql_router_sdk> ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi
            COMMAND ${CMAKE_COMMAND} -E copy  ${CMAKE_BINARY_DIR}/sql_pysdk/openmldb/sql_router_sdk.py ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi
            COMMAND strip ${CMAKE_BINARY_DIR}/python/sqlalchemy_openmldb/openmldbapi/_sql_router_sdk.so
            COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/python/sqlalchemy-openmldb/setup.py ${CMAKE_BINARY_DIR}/python/
            COMMAND ${CMAKE_COMMAND} -E remove_directory dist
            COMMAND cd ${CMAKE_BINARY_DIR}/python/ && ${Python3_EXECUTABLE} setup.py bdist_wheel
            BYPRODUCTS
              python/sqlalchemy-openmldb
              python/build
              python/dist
              python/sqlalchemy-openmldb.egg-info
        )
    endif()
endif()

if(SQL_JAVASDK_ENABLE)
    set_property(SOURCE sql_router_sdk.i PROPERTY CPLUSPLUS ON)
    find_package(Java COMPONENTS Development REQUIRED)
    message(STATUS "Found Java: ${Java_JAVA_EXECUTABLE} (found version \"${Java_VERSION_STRING}\")")
    find_package(JNI REQUIRED)
    message(STATUS "Found JNI: ${JNI_FOUND}")
    # Find maven
    find_program(MAVEN_EXECUTABLE mvn)
    set_property(SOURCE sql_router_sdk.i PROPERTY COMPILE_OPTIONS -package com._4paradigm.openmldb)
    swig_add_library(sql_jsdk
            TYPE SHARED
            LANGUAGE java
            OUTPUT_DIR ${PROJECT_SOURCE_DIR}/java/openmldb-native/src/main/java/com/_4paradigm/openmldb/
            SOURCES sql_router_sdk.i)
    target_include_directories(sql_jsdk PRIVATE ${JNI_INCLUDE_DIRS})
    target_compile_options(sql_jsdk PRIVATE -w)

    add_dependencies(sql_jsdk hybridse_jsdk_core_static)
    if(APPLE)
        target_link_libraries(sql_jsdk PRIVATE "-Wl,-force_load $<TARGET_FILE:hybridse_jsdk_core_static>" ${SDK_LIBS} boost_filesystem)
    else()
        target_link_libraries(sql_jsdk PRIVATE "-Wl,--whole-archive $<TARGET_FILE:hybridse_jsdk_core_static> -Wl,--no-whole-archive" ${SDK_LIBS} boost_filesystem)
    endif()

    if(APPLE)
      set_target_properties(sql_jsdk PROPERTIES
               SUFFIX ".dylib")
      set_property(TARGET sql_jsdk APPEND PROPERTY
        LINK_FLAGS "-flat_namespace -undefined suppress")
    endif()

    # this target ensure cpp compiled libraries were copied into java project:
    # 1. cp sql_jsdk into openmldb-native
    # 2. cp hybridse jsdk into hybridse-native (inside dependency cp_hybridse_native_so)
    add_custom_target(cp_native_so ALL
        COMMAND ${CMAKE_COMMAND} -E make_directory ${PROJECT_SOURCE_DIR}/java/openmldb-native/src/main/resources
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:sql_jsdk> ${PROJECT_SOURCE_DIR}/java/openmldb-native/src/main/resources/
        COMMENT "copy generated native library to java project")
    add_dependencies(cp_native_so cp_hybridse_native_so)

    add_custom_target(sql_javasdk_package ALL
        COMMAND ${MAVEN_EXECUTABLE} clean package -DskipTests=true -Dscalatest.skip=true -Dwagon.skip=true -Dmaven.test.skip=true ${MAVEN_FLAGS}
        WORKING_DIRECTORY ${PROJECT_SOURCE_DIR}/java)
    add_dependencies(sql_javasdk_package cp_native_so)


    FILE(GLOB TASKMANAGER_BIN "${PROJECT_SOURCE_DIR}/java/openmldb-taskmanager/target/openmldb-taskmanager-binary/bin/*")
    FILE(GLOB TASKMANAGER_CONF "${PROJECT_SOURCE_DIR}/java/openmldb-taskmanager/target/openmldb-taskmanager-binary/conf/*")
    FILE(GLOB TASKMANAGER_LIB "${PROJECT_SOURCE_DIR}/java/openmldb-taskmanager/target/openmldb-taskmanager-binary/lib/*")
    install(FILES ${TASKMANAGER_BIN}
            DESTINATION taskmanager/bin/
            PERMISSIONS OWNER_EXECUTE OWNER_WRITE OWNER_READ GROUP_EXECUTE GROUP_READ WORLD_EXECUTE WORLD_READ)
    install(FILES ${TASKMANAGER_CONF}
            DESTINATION taskmanager/conf/
            PERMISSIONS OWNER_WRITE OWNER_READ GROUP_READ WORLD_READ)
    install(FILES ${TASKMANAGER_LIB}
            DESTINATION taskmanager/lib/
            PERMISSIONS OWNER_WRITE OWNER_READ GROUP_READ WORLD_READ)

    FILE(GLOB BATCHJOB_JAR "${PROJECT_SOURCE_DIR}/java/openmldb-batchjob/target/openmldb-batchjob*.jar")
    install(FILES ${BATCHJOB_JAR}
            DESTINATION batchjob/
            PERMISSIONS OWNER_WRITE OWNER_READ GROUP_READ WORLD_READ)
endif()

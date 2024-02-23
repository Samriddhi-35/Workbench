#!/usr/bin/env bash
# Copyright Contributors to the GenevaERS Project. SPDX-License-Identifier: Apache-2.0 (c) Copyright IBM Corporation 2023
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Add the JRE zip if built and variable set
if [ -d "./products/com.ibm.safr.we.product/target/products" ]; then
    if [ ! -z $GERS_JRE ]; then
        echo "Add JRE from GERS_JRE"
        cd ../products/com.ibm.safr.we.product/target/products
        mv $GERS_JRE jre
        7z a wb-win32.win32.x86_64.zip jre/
        mv jre $GERS_JRE
        cd ..
    else
        echo "No JRE included in wb-win32.win32.x86_64.zip"
    fi
    echo "Build Complete"
else
    echo "Build Failed"
fi
if [[ ! -z "$GERS_POST_SCRIPT" ]]; then
    echo "Running custom post-build script from $GERS_POST_SCRIPT"
    $GERS_POST_SCRIPT
fi

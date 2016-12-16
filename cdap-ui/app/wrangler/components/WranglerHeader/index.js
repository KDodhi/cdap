/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import React from 'react';

import HeaderBrand from 'components/HeaderBrand';
import HeaderActions from 'components/HeaderActions';
import NamespaceStore from 'services/NamespaceStore';
import HeaderNavbarList from 'components/HeaderNavbarList';
require('components/Header/Header.less');
require('./WranglerHeader.less');

export default function WranglerHeader() {
  const list = [
    {
      linkTo: '/ns',
      title: 'Import Data'
    },
  ];

  return (
    <div className="cask-header wrangler-header">
      <div className="navbar navbar-fixed-top">
        <nav className="navbar cdap">
          <HeaderBrand/>

          <HeaderNavbarList
            list={list}
            store={NamespaceStore}
          />

          <HeaderActions
            product="wrangler"
          />
        </nav>
      </div>
    </div>
  );
}

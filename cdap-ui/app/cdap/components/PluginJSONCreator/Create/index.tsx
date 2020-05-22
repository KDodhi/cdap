/*
 * Copyright © 2020 Cask Data, Inc.
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

import withStyles, { StyleRules, WithStyles } from '@material-ui/core/styles/withStyles';
import Content from 'components/PluginJSONCreator/Create/Content';
import WizardGuideline from 'components/PluginJSONCreator/Create/WizardGuideline';
import * as React from 'react';

export const CreateContext = React.createContext({});
export const LEFT_PANEL_WIDTH = 250;

const styles = (): StyleRules => {
  return {
    root: {
      height: '100%',
    },
    content: {
      height: 'calc(100% - 50px)',
      display: 'grid',
      gridTemplateColumns: `${LEFT_PANEL_WIDTH}px 1fr`,

      '& > div': {
        overflowY: 'auto',
      },
    },
  };
};

interface ICreateState {
  activeStep: number;
  pluginName: string;
  pluginType: string;
  displayName: string;
  emitAlerts: boolean;
  emitErrors: boolean;
  configurationGroups: IConfigurationGroup[];
  // groupToWidgets: Map<string, string[]>;
  groupToWidgets: any;
  widgetToInfo: any;
  widgetToAttributes: any;
  setActiveStep: (step: number) => void;
  setBasicPluginInfo: (basicPluginInfo: IBasicPluginInfo) => void;
  setConfigurationGroups: (groups: IConfigurationGroup[]) => void;
  setGroupToWidgets: (groupToWidgets: any) => void;
  setWidgetToInfo: (widgetToInfo: any) => void;
  setWidgetToAttributes: (widgetToAttributes: any) => void;
}

export interface IBasicPluginInfo {
  pluginName: string;
  pluginType: string;
  displayName: string;
  emitAlerts: boolean;
  emitErrors: boolean;
}

export interface IConfigurationGroup {
  id: string;
  label: string;
  description?: string;
}

export interface IWidgetInfo {
  name: string;
  label: string;
  widgetType: string;
  widgetCategory?: string;
}

export type ICreateContext = Partial<ICreateState>;

class CreateView extends React.PureComponent<ICreateContext & WithStyles<typeof styles>> {
  public setActiveStep = (activeStep: number) => {
    this.setState({ activeStep });
  };

  public setBasicPluginInfo = (basicPluginInfo: IBasicPluginInfo) => {
    const { pluginName, pluginType, displayName, emitAlerts, emitErrors } = basicPluginInfo;
    this.setState({
      pluginName,
      pluginType,
      displayName,
      emitAlerts,
      emitErrors,
    });
  };

  public setConfigurationGroups = (configurationGroups: IConfigurationGroup[]) => {
    this.setState({ configurationGroups });
  };

  public setGroupToWidgets = (groupToWidgets: any) => {
    this.setState({ groupToWidgets });
  };

  public setWidgetToInfo = (widgetToInfo: any) => {
    this.setState({ widgetToInfo });
  };

  public setWidgetToAttributes = (widgetToAttributes: any) => {
    this.setState({ widgetToAttributes });
  };

  public state = {
    activeStep: 0,
    pluginName: '',
    pluginType: '',
    displayName: '',
    emitAlerts: true,
    emitErrors: true,
    configurationGroups: [],
    // groupToWidgets: new Map<string, string[]>(),
    groupToWidgets: {},
    widgetToInfo: {},
    widgetToAttributes: {},

    setActiveStep: this.setActiveStep,
    setBasicPluginInfo: this.setBasicPluginInfo,
    setConfigurationGroups: this.setConfigurationGroups,
    setGroupToWidgets: this.setGroupToWidgets,
    setWidgetToInfo: this.setWidgetToInfo,
    setWidgetToAttributes: this.setWidgetToAttributes,
  };

  public render() {
    return (
      <CreateContext.Provider value={this.state}>
        <div className={this.props.classes.root}>
          <div className={this.props.classes.content}>
            <WizardGuideline />
            <Content />
          </div>
        </div>
      </CreateContext.Provider>
    );
  }
}

export function createContextConnect(Comp) {
  return (extraProps) => {
    return (
      <CreateContext.Consumer>
        {(props) => {
          const finalProps = {
            ...props,
            ...extraProps,
          };

          return <Comp {...finalProps} />;
        }}
      </CreateContext.Consumer>
    );
  };
}

const Create = withStyles(styles)(CreateView);
export default Create;

// Copyright (c) 2013-2015 Saarland University
// All right reserved.
//
// Author: Konrad Jamrozik, jamrozik@st.cs.uni-saarland.de
//
// This file is part of the "DroidMate" project.
//
// www.droidmate.org

package org.droidmate.exploration.strategy

import org.droidmate.device.datatypes.IGuiState
import org.droidmate.exploration.actions.ExplorationAction

public interface IWidgetStrategy
{
  ExplorationAction decide(IGuiState guiState)

  boolean updateState(IGuiState guiState)
}
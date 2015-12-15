// Copyright (c) 2013-2015 Saarland University
// All right reserved.
//
// Author: Konrad Jamrozik, jamrozik@st.cs.uni-saarland.de
//
// This file is part of the "DroidMate" project.
//
// www.droidmate.org
package org.droidmate.exceptions

import org.droidmate.common.DroidmateException

public class ThrowablesCollection extends DroidmateException
{

  private static final long serialVersionUID = 1

  final List<Throwable> throwables

  public ThrowablesCollection(List<Throwable> throwables)
  {
    super("Aggregating exception holding a collection of ${Throwable.simpleName}s.")
    this.throwables = throwables
  }
}
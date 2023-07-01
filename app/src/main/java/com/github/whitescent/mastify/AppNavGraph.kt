package com.github.whitescent.mastify

import com.ramcosta.composedestinations.annotation.NavGraph
import com.ramcosta.composedestinations.annotation.RootNavGraph

@RootNavGraph(start = true)
@NavGraph
annotation class LoginNavGraph(
  val start: Boolean = false
)

@RootNavGraph
@NavGraph
annotation class AppNavGraph(
  val start: Boolean = false
)

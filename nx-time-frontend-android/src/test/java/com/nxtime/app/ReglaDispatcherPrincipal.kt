package com.nxtime.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sustituye el `Dispatchers.Main` de Android por uno de prueba.
 *
 * `viewModelScope` corre en el hilo principal, que en un test de JVM no
 * existe: sin esto, cualquier ViewModel que lance una corrutina falla
 * con "Module with the Main dispatcher had failed to initialize".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReglaDispatcherPrincipal(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

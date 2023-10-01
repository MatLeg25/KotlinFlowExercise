package com.example.kotlinflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.launch

class MainViewModel(
    private val dispatchers: DispatcherProvider
): ViewModel() {

    //cold flow (if no collectors - do nothing)
    val countDownFlow = flow<Int> {
        val startingValue = 10
        var currentValue = startingValue
        emit(startingValue)
        while (currentValue > 0) {
            delay(1000L)
            currentValue--
            emit(currentValue)
        }
    }.flowOn(dispatchers.main)

    //hot flow (even if no collectors - do updates)
    private val _stateFlow = MutableStateFlow(0)
    val stateFlow = _stateFlow.asStateFlow()

    //by default used for one time events
    private val _sharedFlow = MutableSharedFlow<Int>(replay = 5)
    val sharedFlow = _sharedFlow.asSharedFlow()

    init {
        collectFlow12()

        squareNumber(3)
        viewModelScope.launch(dispatchers.main) {
            sharedFlow.collect {
                delay(2000L)
                println("FIRST FLOW: The received number is $it")
            }
        }
        viewModelScope.launch(dispatchers.main) {
            sharedFlow.collect {
                delay(3000L)
                println("SECOND FLOW: The received number is $it")
            }
        }
    }

//PART1:
// SIMPLE OPERATIONS
    private fun collectFlow1() {
        viewModelScope.launch(dispatchers.main) {
            countDownFlow.collect { time ->
                println("The current time is $time")
            }
        }
    }

    private fun collectFlow2() {
        viewModelScope.launch(dispatchers.main) {
            countDownFlow.collectLatest { time ->
                delay(1500L)
                println("The current time is $time")
            }
        }
    }

    private fun collectFlow3() {
        viewModelScope.launch(dispatchers.main) {
            countDownFlow
                .filter { time ->
                    time % 2 == 0
                }
                .map { filteredTime ->
                    filteredTime * filteredTime
                }
                .collect { mappedTime ->
                    println("The current time is $mappedTime")
                }
        }
    }

    // another way to consume flow
    private fun collectFlow4() {
        countDownFlow.onEach {
            println(it)
        }.launchIn(viewModelScope)
    }


//PART2:
// TERMINAL FLOW OPERATORS (count, reduce, fold)
    private fun collectFlow5() {
        viewModelScope.launch(dispatchers.main) {
            val count = countDownFlow
                .filter { time ->
                    time % 2 == 0
                }
                .map { time ->
                    time * time
                }
                .onEach { time ->
                    println(time)
                }
                .count {
                    it % 2 == 0
                }
            println("The count is $count")
        }
    }

    private fun collectFlow6() {
        viewModelScope.launch(dispatchers.main) {
            val reduceResult = countDownFlow
                .reduce { accumulator, value ->
                    accumulator + value
                }
            println("The reduceResult is $reduceResult")
        }
    }

    private fun collectFlow7() {
        viewModelScope.launch(dispatchers.main) {
            val reduceResult = countDownFlow
                .fold(100) { accumulator, value ->
                    accumulator + value
                }
            println("The reduceResult is $reduceResult")
        }
    }


// FLATTENING OPERATORS
    private fun collectFlow8() {
        val flow1 = flow {
            emit(1)
            delay(500L)
            emit(2)
        }
        viewModelScope.launch(dispatchers.main) {
            flow1.flatMapConcat {  value ->
                flow {
                    emit(value + 100)
                    delay(500L)
                    emit(value + 200)
                }
            }.collect { value ->
                println("The value is $value")
            }
        }
    }



// BUFFER, CONFLATE, COLLECT-LATEST
    //do each operation step-by-step
    private fun collectFlow9() {
        val flow = flow {
            delay(250L)
            emit("Appetizer")
            delay(1000L)
            emit("Main dish")
            delay(100L)
            emit("Dessert")
        }
        viewModelScope.launch(dispatchers.main) {
            flow.onEach {
                println("FLOW: $it is delivered")
            }.collect {
                println("FLOW: Now eating $it")
                delay(1500L)
                println("FLOW: Finished eating $it")
            }
        }
    }

    //buffer - splits flow and collect to separated coroutines,
    //each emit from the flow is handled separately, it does not wait until the block below the buffer is executed
    private fun collectFlow10() {
        val flow = flow {
            delay(250L)
            emit("Appetizer")
            delay(1000L)
            emit("Main dish")
            delay(100L)
            emit("Dessert")
        }
        viewModelScope.launch(dispatchers.main) {
            flow.onEach {
                println("FLOW: $it is delivered")
            }
                .buffer()
                .collect {
                println("FLOW: Now eating $it")
                delay(1500L)
                println("FLOW: Finished eating $it")
            }
        }
    }

    //conflate - splits flow and collect to separated coroutines,
    //if there are emissions from the flow that we cannot collect yet, when we finish the current emissions,
    // we go directly to the newest one (we abandon emissions before the last one)
    private fun collectFlow11() {
        val flow = flow {
            delay(250L)
            emit("Appetizer")
            delay(1000L)
            emit("Main dish")
            delay(100L)
            emit("Dessert")
        }
        viewModelScope.launch(dispatchers.main) {
            flow.onEach {
                println("FLOW: $it is delivered")
            }
                .conflate()
                .collect {
                    println("FLOW: Now eating $it")
                    delay(1500L)
                    println("FLOW: Finished eating $it")
                }
        }
    }

    // we go directly to the newest one (we stop dealing with the current one)
    private fun collectFlow12() {
        val flow = flow {
            delay(250L)
            emit("Appetizer")
            delay(1000L)
            emit("Main dish")
            delay(100L)
            emit("Dessert")
        }
        viewModelScope.launch(dispatchers.main) {
            flow.onEach {
                println("FLOW: $it is delivered")
            }
                .collectLatest {
                    println("FLOW: Now eating $it")
                    delay(1500L)
                    println("FLOW: Finished eating $it")
                }
        }
    }


//PART3: StateFlow  & SharedFlow
    fun incrementCounter() {
        _stateFlow.value += 1
    }

    fun squareNumber(number: Int) {
        viewModelScope.launch(dispatchers.main) {
            _sharedFlow.emit(number * number)
        }
    }



    // ViewModel with dependency, (added for tests)
    // Define ViewModel factory in a companion object
    companion object {

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {

                val dispatcher = DefaultDispatchers()

                return MainViewModel(dispatcher) as T
            }
        }
    }
}


import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

// Coroutines Flowでのバッファー状態による動作の違いを完全説明

@OptIn(ExperimentalCoroutinesApi::class)
fun main() {
    runBlocking {
        val repository = Repository()
        val emitter = Emitter()


        launch {
            emitter.changeText("A")
            emitter.changeText("B")
            emitter.changeText("C")
        }

//        emitter.stateFlow.flatMapConcat {
//            repository.load(it)
//        }.collect {
//            println(it)

/*
collectを遅らせるとどうなる
*/
//        delay(400)
//
//        emitter.sharedFlow.flatMapConcat {
//            repository.load(it)
//        }.collect {
//            println(it)
//        }

        emitter.sharedFlow.flatMapConcat {
            repository.load(it)
        }.collect {
            println(it)
/*
表示される値
A_1
A_2
A_3
B_1
B_2
B_3
C_1
C_2
C_3
*/
        }

        emitter.sharedFlow
            .flatMapMerge {
                repository.load(it)
//            }.collect {
//                println(it)
/*
表示される値
A_1
B_1
C_1
A_2
B_2
C_2
A_3
B_3
C_3
*/
            }

        emitter.sharedFlow
            .flatMapLatest {
                repository.load(it)
//            }.collect {
//                println(it)
/*
表示される値
C_1
C_2
C_3
*/
            }
    }
}

class Emitter {
    /*
    stateFlowは以下と同じ
        private val _shared =
            MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val sharedFlow: SharedFlow<String>
            get() {
                _shared.tryEmit("Z")
                return _shared.asSharedFlow()
            }
    */
    private val _state: MutableStateFlow<String> = MutableStateFlow("Z")
    val stateFlow: StateFlow<String> = _state.asStateFlow()

    /*
        動作の補足
         -Aはすぐに動作するのでbufferとか関係ない。BとCがバッファーの対象
         -flatMapConcatは内側の処理が終わってから、次の外側の処理にいくのでBufferに入る(loadが1回動いてから次へ)
         -flatMapMerge,flatMapLatestは外の処理は全てすぐに終わり、次の処理に入るためBuffer不要(loadが3回動いてから次へ)
    */
/*
     - MutableSharedFlowのパラメーターについて
     flatMapConcatの場合だけ関係するflatMapMergeはすぐに次の処理に入るためBuffer不要
        extraBufferCapacity=1, DROP_OLDESTにするとBがキャンセル(Bのloadは呼ばれない)
        extraBufferCapacity=1, DROP_LATESTにするとCがキャンセル(Cのloadは呼ばれない)
      */
/*
        extraBufferCapacityとreplayの違い
        extraBufferCapacity=0, replay = 1, onBufferOverflow = DROP_OLDESTにするとBがloadされない
        replay = 0の場合、emitした値はその時点で存在しているSubscriberにだけ出力される。
        replay = 1の場合、Subscriberが存在してもしなくても、最後にemitされた値が１つSharedFlow内に保持され、Subscriberがcollectするとバッファーの値が出力される
        extraBufferCapacityの場合は、emitされた値を格納しておくバッファのサイズ
*/
    private val _shared =
        MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
    val sharedFlow: SharedFlow<String> = _shared.asSharedFlow()

    suspend fun changeText(text: String) {
        delay(100)
/*
suspendされる状態でtryEmitにすると
SUSPENDでbufferがない場合はemitできないので何も送られない
replay=1 or extraBufferCapacity=1にするとsuspend状態になるCがエラーになる。つまり、DROP_LATESTと同じになる
*/
//        println(_shared.tryEmit(text))
        _shared.emit(text)
        println("emitの次の処理")
        _state.value = text
    }
}

class Repository {

    fun load(param: String) = flowOf(1, 2, 3)
        .onEach { delay(500) }
        .map { "${param}_${it}" }
}

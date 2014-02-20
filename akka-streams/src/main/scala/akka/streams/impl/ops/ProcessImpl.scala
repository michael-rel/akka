package akka.streams.impl.ops

import akka.streams.impl._
import akka.streams.Operation.Process

class ProcessImpl[A, B, S](upstream: Upstream, downstream: Downstream[B], op: Process[A, B, S], batchSize: Int = 1) extends DynamicSyncOperation[A] {
  override def initial = Running

  def Running = new State {
    private var state = op.seed
    def handleRequestMore(n: Int): Effect = upstream.requestMore(batchSize)
    def handleCancel(): Effect = upstream.cancel

    def handleNext(element: A): Effect = handleCommand(op.onNext(state, element)) ~ upstream.requestMore(1)

    import Process.{ Continue ⇒ _, _ }
    def handleCommand(command: Command[B, S]): Effect =
      command match {
        case Process.Continue(newState) ⇒
          state = newState; Continue
        case Emit(value, andThen) ⇒ downstream.next(value) ~ handleCommand(andThen)
        case Stop                 ⇒ downstream.complete ~ upstream.cancel
      }

    def handleComplete(): Effect = {
      become(Stopped)
      op.onComplete(state).foldLeft(Continue: Effect)((e, element) ⇒ e ~ downstream.next(element)) ~ downstream.complete ~ upstream.cancel
    }
    def handleError(cause: Throwable): Effect = {
      become(Stopped)
      downstream.error(cause)
    }
  }
}

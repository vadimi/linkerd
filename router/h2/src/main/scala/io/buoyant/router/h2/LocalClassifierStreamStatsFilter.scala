package io.buoyant.router.h2

import com.twitter.finagle.param
import com.twitter.finagle._
import com.twitter.finagle.buoyant.h2.{Request, Response}
import com.twitter.finagle.buoyant.h2.param.H2StreamClassifier
import io.buoyant.router.{PerDstPathFilter, PerDstPathStatsFilter}
import io.buoyant.router.context.h2.StreamClassifierCtx

/**
 * Like [[io.buoyant.router.LocalClassifierStatsFilter]],
 * but specialized for H2 streams.
 */
object LocalClassifierStreamStatsFilter {

  def module: Stackable[ServiceFactory[Request, Response]] =
    new Stack.Module2[param.Stats, StreamStatsFilter.Param, ServiceFactory[Request, Response]] {
      val role: Stack.Role = PerDstPathStatsFilter.role
      val description = "Report request statistics for each logical destination"

      override def make(
        statsP: param.Stats,
        statsFilterP: StreamStatsFilter.Param,
        next: ServiceFactory[Request, Response]
      ): ServiceFactory[Request, Response] =
        statsP match {
          case param.Stats(stats) if !stats.isNull =>
            val StreamStatsFilter.Param(timeUnit) = statsFilterP

            // We memoize on the dst path.  The assumes that the response classifier for a dst path
            // never changes.
            def mkClassifiedStatsFilter(path: Path): SimpleFilter[Request, Response] = {
              val H2StreamClassifier(classifier) =
                StreamClassifierCtx.current.getOrElse(H2StreamClassifier.param.default)
              new StreamStatsFilter(stats, classifier, timeUnit)
            }

            val filter = new PerDstPathFilter(mkClassifiedStatsFilter _)
            filter.andThen(next)

          // can this actually be null? the HTTP1 `PerDstPathStatsFilter`
          // checks for this case, so i figured we ought to here as well...
          case _ => next

        }
    }

}

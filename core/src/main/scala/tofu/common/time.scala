package tofu.common

import java.time._
import java.util.Objects

import cats.Monad
import cats.effect.{Clock, Sync}
import cats.tagless.autoFlatMap
import simulacrum.typeclass
import tofu.optics.data.Constant.Apply

import scala.annotation.tailrec
import scala.collection.JavaConverters._

/**
  * wrapping around ZoneId methods */
trait TimeZone[F[_]] {

  /** current System time zone */
  def system: F[ZoneId]

  /** list of available time zones */
  def available: F[Set[String]]

  /** get ZoneId by full string ID,
    * checking for availability, probably generating an error
    * see documentation to `java.time.ZoneId.of` */
  def of(zoneId: String): F[ZoneId]

  /**
    * Obtains an instance of {@code ZoneId} wrapping an offset.
    * <p>
    * If the prefix is "GMT", "UTC", or "UT" a {@code ZoneId}
    * with the prefix and the non-zero offset is returned.
    * If the prefix is empty {@code ""} the {@code ZoneOffset} is returned.
    */
  def ofOffset(prefix: String, offset: ZoneOffset): F[ZoneId]
}

object TimeZone {
  implicit def syncSystem[F[_]](implicit F: Sync[F]): TimeZone[F] = new TimeZone[F] {
    def system: F[ZoneId] = F.delay(ZoneId.systemDefault())

    def available: F[Set[String]] = F.delay(ZoneId.getAvailableZoneIds.asScala.toSet)

    def of(zoneId: String): F[ZoneId] = F.delay(ZoneId.of(zoneId))

    def ofOffset(prefix: String, offset: ZoneOffset): F[ZoneId] = F.delay(ZoneId.ofOffset(prefix, offset))
  }
}

/** typeclass for the types describing some time moment information
  * possibly respecting timezone */
trait TimeData[A] {

  /** construct an instance from moment in time and time zone */
  def fromInstant(instant: Instant, zoneId: ZoneId): A

  final def map[B](f: A => B): TimeData[B] = (instant, zoneId) => f(fromInstant(instant, zoneId))
}

object TimeData {

  def apply[A](implicit td: TimeData[A]): TimeData[A] = td

  implicit val timeDataMonad: Monad[TimeData] = new Monad[TimeData] {
    def pure[A](x: A): TimeData[A] = (_, _) => x

    override def map[A, B](fa: TimeData[A])(f: A => B): TimeData[B] = fa.map(f)

    def flatMap[A, B](fa: TimeData[A])(f: A => TimeData[B]): TimeData[B] =
      (instant, zoneId) => f(fa.fromInstant(instant, zoneId)).fromInstant(instant, zoneId)

    def tailRecM[A, B](a: A)(f: A => TimeData[Either[A, B]]): TimeData[B] =
      (instant, zoneId) => {
        @tailrec def go(a: A): B = f(a).fromInstant(instant, zoneId) match {
          case Left(a)  => go(a)
          case Right(b) => b
        }
        go(a)
      }
  }

  implicit val instant: TimeData[Instant]               = (inst, _) => inst
  implicit val zonedDateTime: TimeData[ZonedDateTime]   = ZonedDateTime.ofInstant(_, _)
  implicit val localDateTime: TimeData[LocalDateTime]   = LocalDateTime.ofInstant(_, _)
  implicit val localDate: TimeData[LocalDate]           = LocalDate.ofInstant(_, _)
  implicit val localTime: TimeData[LocalTime]           = LocalTime.ofInstant(_, _)
  implicit val offsetDateTime: TimeData[OffsetDateTime] = OffsetDateTime.ofInstant(_, _)
  implicit val offsetTime: TimeData[OffsetTime]         = OffsetTime.ofInstant(_, _)
  implicit val month: TimeData[Month]                   = zonedDateTime.map(_.getMonth)
  implicit val monthDay: TimeData[MonthDay]             = zonedDateTime.map(zdt => MonthDay.of(zdt.getMonth, zdt.getDayOfMonth))
  implicit val dayOfWeek: TimeData[DayOfWeek]           = zonedDateTime.map(_.getDayOfWeek)
  implicit val year: TimeData[Year]                     = zonedDateTime.map(zdt => Year.of(zdt.getYear))
}

package net.shrine.utilities.scanner.commands

import net.shrine.utilities.scanner.csv.CsvData
import net.shrine.utilities.scanner.ScanResults
import net.shrine.utilities.scanner.Disposition
import net.shrine.utilities.scanner.csv.CsvRow

/**
 * @author clint
 * @date Mar 25, 2013
 */
case object ToCsvData extends (ScanResults >>> CsvData) {
  override def apply(scanResults: ScanResults): CsvData = {
    def applyDisposition(disposition: Disposition)(terms: Iterable[String]) = {
      terms.toSeq.map(term => CsvRow(disposition, term))
    }
    
    def toNeverFinishedRows = applyDisposition(Disposition.NeverFinished) _
    
    def toShouldHaveBeenMappedRows = applyDisposition(Disposition.ShouldHaveBeenMapped) _
    
    def toShouldNotHaveBeenMappedRows = applyDisposition(Disposition.ShouldNotHaveBeenMapped) _
    
    val rows = toShouldHaveBeenMappedRows(scanResults.shouldHaveBeenMapped) ++ toShouldNotHaveBeenMappedRows(scanResults.shouldNotHaveBeenMapped) ++ toNeverFinishedRows(scanResults.neverFinished)
    
    CsvData(rows)
  }
}
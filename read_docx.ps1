$word = New-Object -ComObject Word.Application
$doc = $word.Documents.Open("$PWD\default.docx")
$doc.Content.Text | Out-File -FilePath "temp_output.txt" -Encoding UTF8
$doc.Close($false)
$word.Quit()

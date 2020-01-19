@ECHO OFF

:: Optional - edit the restored world name ::
set worlddir=RestoredWorld

:: Optional - manually select backup folder (default is latest) ::
set backupdir=

:: Don't edit past this point ::
for /f "tokens=*" %%f in (
	'dir /b mods\Backups-*.jar'
) do (
	set modname=%%f
	goto :foundmod
)
echo Backups mod was not found
goto :done
:foundmod
set modpath=mods\%modname%

if [%backupdir%]==[] (
	for /f "tokens=*" %%a in (
		'dir /b /ad-h /t:c /o-d "backups"'
	) do for /f "tokens=*" %%b in (
			'dir /b /ad-h /t:c /o-d "backups\%%a"'
		) do (
			set latestworld=%%a
			set latestsave=%%b
			goto :foundbackup 
		)
	echo No backups were found
	goto :done
	:foundbackup
	set backupdir=backups\%latestworld%\%latestsave%
)

java -jar "%modpath%" "%backupdir%" "%worlddir%"

if %errorlevel%==0 (
	echo World backup successfully restored to %worlddir%!
)

:done

PAUSE
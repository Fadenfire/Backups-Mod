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

if not [%backupdir%]==[] goto :backup

if exist backupsmod (
	set mainbackupsdir=backupsmod
) else if exist backups (
	set mainbackupsdir=backups
) else (
	echo No backups were found 
	goto :done
)

for /f "tokens=*" %%a in (
	'dir /b /ad-h /t:c /o-d "%mainbackupsdir%"'
) do for /f "tokens=*" %%b in (
		'dir /b /ad-h /t:c /o-d "%mainbackupsdir%\%%a"'
	) do (
		set latestworld=%%a
		set latestsave=%%b
		goto :foundbackup 
	)
echo No backups were found
goto :done

:foundbackup
set backupdir=%mainbackupsdir%\%latestworld%\%latestsave%

:backup
java -jar "%modpath%" "%backupdir%" "%worlddir%"

if %errorlevel%==0 (
	echo World backup successfully restored to [%worlddir%] from [%backupdir%]
)

:done

PAUSE

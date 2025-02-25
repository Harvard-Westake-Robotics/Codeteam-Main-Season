package frc.OscarLib.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.littletonrobotics.junction.Logger;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import com.pathplanner.lib.commands.PathPlannerAuto;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.pathplanner.lib.path.PathConstraints;
import com.pathplanner.lib.path.PathPlannerPath;
import com.pathplanner.lib.util.PathPlannerLogging;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import frc.OscarLib.lib.Dashboards.ShuffleDashboard;
import frc.OscarLib.lib.Logging.PLog;
import frc.OscarLib.lib.Swerve.SwerveConstants;
import frc.OscarLib.lib.Swerve.SwerveDriveSubsystem;

public class Auto {
    public static class NamedCommand {
        public String name;
        public Command command;

        public NamedCommand(String name, Command command) {
            this.name = name;
            this.command = command;
        }

        public void register() {
            NamedCommands.registerCommand(name, command);
        }
    }

    RobotConfig config;

    public static Auto m_instance;
    private SwerveDriveSubsystem _swerve;

    private final SendableChooser<Command> autoChooser;

    private Auto (SwerveDriveSubsystem swerve, NamedCommand... commandsToRegister) {
        try{
            config = RobotConfig.fromGUISettings();
          } catch (Exception e) {
            // Handle exception as needed
            e.printStackTrace();
          }
      
        _swerve = swerve;
        


        AutoBuilder.configure(
            _swerve::getPose, 
            _swerve::resetPose, 
            _swerve::getRobotRelativeSpeeds,
            (speeds, feedforwards) -> _swerve.Drive(speeds), 
            new PPHolonomicDriveController(new PIDConstants(5.0, 0.0, 0.0), new PIDConstants(5.0, 0.0, 0.0)),
            config,
            () -> {
                Optional<DriverStation.Alliance> alliance = DriverStation.getAlliance();
                if (alliance.isPresent()) {
                    return alliance.get() == DriverStation.Alliance.Red;
                }
                return false;
            },
            _swerve
        );

        for (NamedCommand namedCommand : commandsToRegister) {
            namedCommand.register();
        }

        PathPlannerLogging.setLogTargetPoseCallback((pose) -> {
            Logger.recordOutput("696/Auto/Desired", pose);
        });

        PathPlannerLogging.setLogActivePathCallback((poses) -> {
            ShuffleDashboard.field.getObject("Path").setPoses(poses);
        });

        autoChooser = AutoBuilder.buildAutoChooser();
        ShuffleDashboard.addAutoChooser(autoChooser);

        autoChooser.onChange((command)-> {
            visualize();
        });
    }

    public static void Initialize(SwerveDriveSubsystem swerve, NamedCommand... commandsToRegister){
        if (m_instance != null) throw new RuntimeException ("Don't Initialize Twice!");
        
        m_instance = new Auto(swerve, commandsToRegister);
    }

    public static Auto get(){
        if (m_instance == null) throw new RuntimeException ("Please Initialize First!");

        return m_instance;
    }

    public static Command Selected() {
        if (m_instance == null) return new WaitCommand(0);

        return m_instance.autoChooser.getSelected();
    }

    public static Command SelectedEndAt15() {
        return Selected().raceWith(new WaitCommand(15.0));
    }

    public static Command PathFind(Pose2d end) {
        return AutoBuilder.pathfindToPose(end, new PathConstraints(1, 1, Math.PI,Math.PI));
    }

    public Command PathFindToAutoBeginning() {
        Pose2d initialPose;
        try {
            List<PathPlannerPath> paths = PathPlannerAuto.getPathGroupFromAutoFile(autoChooser.getSelected().getName());

            if (paths.size() <= 0) return new WaitCommand(0);

            initialPose = paths.get(0).getStartingHolonomicPose().get();
        } catch (Exception e) {
            PLog.fatalException("Auto", "Failed To Find Path", e);
            return new WaitCommand(0);
        }

        return AutoBuilder.pathfindToPose(initialPose, new PathConstraints(1, 1, Math.PI, Math.PI));
    }

    public void visualize() {
        List<PathPlannerPath> paths;
        List<Pose2d> pathPoses = new ArrayList<Pose2d>();
        try {
            paths = PathPlannerAuto.getPathGroupFromAutoFile(autoChooser.getSelected().getName());
        } catch (Exception e) {
            PLog.fatalException("Auto", "Failed To Find Path", e);
            return;
        }
        for (int i = 0; i < paths.size(); i++) {
            PathPlannerPath path = paths.get(i);
            if (DriverStation.getAlliance().isPresent() && DriverStation.getAlliance().get() == DriverStation.Alliance.Red)
                path = path.flipPath();
            pathPoses.addAll(path.getPathPoses());
        }
        ShuffleDashboard.field.getObject("traj").setPoses(pathPoses);
    }
}